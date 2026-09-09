"""Backoffice user, subscription and server management."""
from datetime import datetime, timedelta

import pytest

SERVER = {
    "id": "mgmt-1", "country": "Testland", "city": "Testville",
    "ip_address": "203.0.113.5", "is_premium": False,
    "status": "online", "load_percent": 20,
    "wg_public_key": "dGVzdC1wdWJsaWMta2V5LWJhc2U2NC1wYWRkaW5nPQ==",
    "wg_endpoint": "203.0.113.5:51820",
}


def auth(token):
    return {"Authorization": f"Bearer {token}"}


# ---- Access control ----

ADMIN_ROUTES = [
    ("get", "/admin/stats"),
    ("get", "/admin/users"),
    ("get", "/admin/users/someone"),
    ("patch", "/admin/users/someone"),
    ("delete", "/admin/users/someone"),
    ("post", "/admin/users/someone/subscription"),
    ("delete", "/admin/users/someone/subscription"),
    ("get", "/admin/servers"),
    ("get", "/admin/servers/mgmt-1"),
    ("put", "/admin/servers/mgmt-1"),
    ("delete", "/admin/servers/mgmt-1"),
]


@pytest.mark.parametrize(("method", "path"), ADMIN_ROUTES)
def test_every_admin_route_rejects_anonymous(client, method, path):
    assert getattr(client, method)(path).status_code == 401


@pytest.mark.parametrize(("method", "path"), ADMIN_ROUTES)
def test_every_admin_route_rejects_a_normal_user(client, user_token, method, path):
    resp = getattr(client, method)(path, headers=auth(user_token))
    assert resp.status_code == 403


# ---- Stats ----

def test_stats_counts_users_and_servers(client, admin_token, make_user):
    make_user(email="extra@test.local")
    client.post("/admin/servers", json=SERVER, headers=auth(admin_token))

    body = client.get("/admin/stats", headers=auth(admin_token)).json()
    assert body["total_users"] == 2
    assert body["admin_users"] == 1
    assert body["total_servers"] == 1
    assert body["online_servers"] == 1


def test_stats_excludes_expired_subscriptions_from_premium_count(
    client, admin_token, make_user, db
):
    from backend import models
    user = make_user(email="lapsed@test.local", is_premium=True)
    user.subscription_expiry = datetime.utcnow() - timedelta(days=1)
    db.commit()

    body = client.get("/admin/stats", headers=auth(admin_token)).json()
    assert body["premium_users"] == 0
    assert db.query(models.DbUser).count() == 2


# ---- User management ----

def test_list_users_paginates(client, admin_token, make_user):
    for i in range(5):
        make_user(email=f"u{i}@test.local")

    body = client.get("/admin/users?limit=2&offset=0", headers=auth(admin_token)).json()
    assert body["total"] == 6  # 5 + the admin
    assert len(body["items"]) == 2
    assert body["limit"] == 2


def test_list_users_searches_by_email(client, admin_token, make_user):
    make_user(email="findme@test.local")
    make_user(email="other@test.local")

    body = client.get("/admin/users?search=findme", headers=auth(admin_token)).json()
    assert body["total"] == 1
    assert body["items"][0]["email"] == "findme@test.local"


def test_get_unknown_user_returns_404(client, admin_token):
    resp = client.get("/admin/users/nope", headers=auth(admin_token))
    assert resp.status_code == 404


def test_patch_user_applies_only_supplied_fields(client, admin_token, make_user):
    user = make_user(email="target@test.local", is_premium=True)
    resp = client.patch(f"/admin/users/{user.id}", json={"is_active": False},
                        headers=auth(admin_token))
    assert resp.status_code == 200
    body = resp.json()
    assert body["is_active"] is False
    assert body["is_premium"] is True, "unsupplied fields must be left alone"


def test_deactivated_admin_cannot_log_in(client, admin_token, make_user):
    other = make_user(email="second@test.local", password="pw12345678", is_admin=True)
    client.patch(f"/admin/users/{other.id}", json={"is_active": False},
                 headers=auth(admin_token))

    resp = client.post("/admin/auth/login", data={
        "username": "second@test.local", "password": "pw12345678",
    })
    assert resp.status_code == 401


def test_delete_user(client, admin_token, make_user):
    user = make_user(email="doomed@test.local")
    assert client.delete(f"/admin/users/{user.id}",
                         headers=auth(admin_token)).status_code == 204
    assert client.get(f"/admin/users/{user.id}",
                      headers=auth(admin_token)).status_code == 404


# ---- Lockout guards ----

def test_last_admin_cannot_demote_themselves(client, admin_token, db):
    from backend import models
    me = db.query(models.DbUser).filter(models.DbUser.is_admin.is_(True)).first()

    resp = client.patch(f"/admin/users/{me.id}", json={"is_admin": False},
                        headers=auth(admin_token))
    assert resp.status_code == 409
    db.refresh(me)
    assert me.is_admin is True


def test_last_admin_cannot_deactivate_themselves(client, admin_token, db):
    from backend import models
    me = db.query(models.DbUser).filter(models.DbUser.is_admin.is_(True)).first()

    resp = client.patch(f"/admin/users/{me.id}", json={"is_active": False},
                        headers=auth(admin_token))
    assert resp.status_code == 409


def test_admin_cannot_delete_their_own_account(client, admin_token, db):
    from backend import models
    me = db.query(models.DbUser).filter(models.DbUser.is_admin.is_(True)).first()

    resp = client.delete(f"/admin/users/{me.id}", headers=auth(admin_token))
    assert resp.status_code == 409


def test_an_admin_can_be_demoted_when_another_admin_remains(
    client, admin_token, make_user
):
    other = make_user(email="second@test.local", is_admin=True)
    resp = client.patch(f"/admin/users/{other.id}", json={"is_admin": False},
                        headers=auth(admin_token))
    assert resp.status_code == 200
    assert resp.json()["is_admin"] is False


# ---- Subscriptions ----

def test_grant_subscription_makes_the_user_premium(client, admin_token, make_user):
    user = make_user(email="buyer@test.local")
    resp = client.post(f"/admin/users/{user.id}/subscription",
                       json={"plan": "monthly", "duration_days": 30},
                       headers=auth(admin_token))
    assert resp.status_code == 200
    body = resp.json()
    assert body["is_premium"] is True
    assert body["subscription_plan"] == "monthly"

    expiry = datetime.fromisoformat(body["subscription_expiry"])
    assert timedelta(days=29) < expiry - datetime.utcnow() < timedelta(days=31)


def test_grant_extends_from_the_existing_expiry(client, admin_token, make_user):
    """Renewing early must not cost the user their remaining days."""
    user = make_user(email="renewer@test.local")
    client.post(f"/admin/users/{user.id}/subscription",
                json={"plan": "monthly", "duration_days": 30},
                headers=auth(admin_token))
    body = client.post(f"/admin/users/{user.id}/subscription",
                       json={"plan": "monthly", "duration_days": 30},
                       headers=auth(admin_token)).json()

    expiry = datetime.fromisoformat(body["subscription_expiry"])
    assert expiry - datetime.utcnow() > timedelta(days=59)


def test_grant_with_extend_false_replaces_the_window(client, admin_token, make_user):
    user = make_user(email="reset@test.local")
    client.post(f"/admin/users/{user.id}/subscription",
                json={"plan": "yearly", "duration_days": 365},
                headers=auth(admin_token))
    body = client.post(f"/admin/users/{user.id}/subscription",
                       json={"plan": "trial", "duration_days": 7, "extend": False},
                       headers=auth(admin_token)).json()

    expiry = datetime.fromisoformat(body["subscription_expiry"])
    assert expiry - datetime.utcnow() < timedelta(days=8)
    assert body["subscription_plan"] == "trial"


def test_revoke_subscription_clears_premium(client, admin_token, make_user):
    user = make_user(email="cancel@test.local")
    client.post(f"/admin/users/{user.id}/subscription",
                json={"plan": "monthly", "duration_days": 30},
                headers=auth(admin_token))

    body = client.delete(f"/admin/users/{user.id}/subscription",
                         headers=auth(admin_token)).json()
    assert body["is_premium"] is False
    assert body["subscription_expiry"] is None
    assert body["subscription_plan"] is None


@pytest.mark.parametrize("payload", [
    {"plan": "monthly", "duration_days": 0},
    {"plan": "monthly", "duration_days": -5},
    {"plan": "", "duration_days": 30},
    {"duration_days": 30},
])
def test_grant_rejects_invalid_input(client, admin_token, make_user, payload):
    user = make_user(email="validate@test.local")
    resp = client.post(f"/admin/users/{user.id}/subscription", json=payload,
                       headers=auth(admin_token))
    assert resp.status_code == 422


# ---- Server management ----

def test_server_lifecycle(client, admin_token):
    created = client.post("/admin/servers", json=SERVER, headers=auth(admin_token))
    assert created.status_code == 201

    fetched = client.get("/admin/servers/mgmt-1", headers=auth(admin_token))
    assert fetched.json()["city"] == "Testville"

    updated = client.put("/admin/servers/mgmt-1",
                         json={"status": "offline", "load_percent": 90},
                         headers=auth(admin_token))
    assert updated.status_code == 200
    assert updated.json()["status"] == "offline"
    assert updated.json()["city"] == "Testville", "partial update must not blank fields"

    assert client.delete("/admin/servers/mgmt-1",
                         headers=auth(admin_token)).status_code == 204
    assert client.get("/admin/servers/mgmt-1",
                      headers=auth(admin_token)).status_code == 404


def test_duplicate_server_id_is_rejected(client, admin_token):
    client.post("/admin/servers", json=SERVER, headers=auth(admin_token))
    resp = client.post("/admin/servers", json=SERVER, headers=auth(admin_token))
    assert resp.status_code == 409


def test_update_unknown_server_returns_404(client, admin_token):
    resp = client.put("/admin/servers/ghost", json={"status": "offline"},
                      headers=auth(admin_token))
    assert resp.status_code == 404


@pytest.mark.parametrize("payload", [
    {"load_percent": 101},
    {"load_percent": -1},
    {"keepalive": -1},
])
def test_server_update_rejects_out_of_range_values(client, admin_token, payload):
    client.post("/admin/servers", json=SERVER, headers=auth(admin_token))
    resp = client.put("/admin/servers/mgmt-1", json=payload,
                      headers=auth(admin_token))
    assert resp.status_code == 422


def test_admin_server_list_paginates(client, admin_token):
    for i in range(3):
        client.post("/admin/servers", json={**SERVER, "id": f"srv-{i}"},
                    headers=auth(admin_token))

    body = client.get("/admin/servers?limit=2", headers=auth(admin_token)).json()
    assert body["total"] == 3
    assert len(body["items"]) == 2


@pytest.mark.parametrize("bad", [
    {"load_percent": 101},
    {"load_percent": -1},
    {"keepalive": -1},
    {"id": ""},
])
def test_server_create_rejects_out_of_range_values(client, admin_token, bad):
    """Create must enforce the same bounds as update."""
    resp = client.post("/admin/servers", json={**SERVER, **bad},
                       headers=auth(admin_token))
    assert resp.status_code == 422
