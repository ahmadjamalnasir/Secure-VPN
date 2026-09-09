"""Authentication and authorisation behaviour of the public/mobile API."""


def test_health_reports_healthy(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "healthy"}


def test_root_is_public(client):
    assert client.get("/").status_code == 200


def test_signup_returns_a_usable_token(client):
    resp = client.post("/auth/signup", json={
        "email": "new@test.local", "password": "password123",
    })
    assert resp.status_code == 200
    body = resp.json()
    assert body["token_type"] == "bearer"
    assert body["is_admin"] is False
    assert body["is_premium"] is False

    me = client.get("/users/me", headers={
        "Authorization": f"Bearer {body['access_token']}",
    })
    assert me.status_code == 200
    assert me.json()["email"] == "new@test.local"


def test_signup_rejects_duplicate_email(client):
    payload = {"email": "dupe@test.local", "password": "password123"}
    assert client.post("/auth/signup", json=payload).status_code == 200
    second = client.post("/auth/signup", json=payload)
    assert second.status_code == 400
    assert "already registered" in second.json()["detail"].lower()


def test_signup_never_stores_the_password_in_clear(client, db):
    from backend import models
    client.post("/auth/signup", json={
        "email": "hash@test.local", "password": "password123",
    })
    user = db.query(models.DbUser).filter(
        models.DbUser.email == "hash@test.local"
    ).first()
    assert user.hashed_password != "password123"
    assert user.hashed_password.startswith("$2")


def test_login_succeeds_with_correct_credentials(client, make_user):
    make_user(email="login@test.local", password="password123")
    resp = client.post("/auth/login", json={
        "email": "login@test.local", "password": "password123",
    })
    assert resp.status_code == 200
    assert resp.json()["access_token"]


def test_login_rejects_wrong_password(client, make_user):
    make_user(email="login@test.local", password="password123")
    resp = client.post("/auth/login", json={
        "email": "login@test.local", "password": "wrong",
    })
    assert resp.status_code == 401


def test_login_rejects_unknown_email(client):
    resp = client.post("/auth/login", json={
        "email": "ghost@test.local", "password": "password123",
    })
    assert resp.status_code == 401


def test_users_me_requires_a_token(client):
    assert client.get("/users/me").status_code == 401


def test_users_me_rejects_a_garbage_token(client):
    resp = client.get("/users/me", headers={"Authorization": "Bearer not-a-jwt"})
    assert resp.status_code == 401


def test_token_signed_with_another_key_is_rejected(client, make_user):
    """A token forged with a different secret must not be accepted.

    This is the regression test for the JWT_SECRET_KEY / SECRET_KEY mismatch
    that let the published fallback key sign valid tokens.
    """
    from datetime import datetime, timedelta

    from jose import jwt

    make_user(email="victim@test.local")
    forged = jwt.encode(
        {"sub": "victim@test.local",
         "exp": datetime.utcnow() + timedelta(minutes=30)},
        "your-256-bit-secret-dev-key",  # the old published fallback
        algorithm="HS256",
    )
    resp = client.get("/users/me", headers={"Authorization": f"Bearer {forged}"})
    assert resp.status_code == 401
