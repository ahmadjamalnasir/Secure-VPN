"""Admin backoffice access control and the server endpoints."""


def test_admin_login_succeeds_for_an_admin(client, make_user):
    make_user(email="admin@test.local", password="adminpass123", is_admin=True)
    resp = client.post("/admin/auth/login", data={
        "username": "admin@test.local", "password": "adminpass123",
    })
    assert resp.status_code == 200
    assert resp.json()["is_admin"] is True


def test_admin_login_rejects_a_non_admin_with_valid_credentials(client, make_user):
    """A normal user's correct password must not open the backoffice."""
    make_user(email="normal@test.local", password="password123", is_admin=False)
    resp = client.post("/admin/auth/login", data={
        "username": "normal@test.local", "password": "password123",
    })
    assert resp.status_code == 401


def test_admin_endpoints_reject_anonymous_requests(client):
    assert client.get("/admin/users").status_code == 401


def test_admin_endpoints_reject_a_normal_user_token(client, user_token):
    resp = client.get("/admin/users", headers={
        "Authorization": f"Bearer {user_token}",
    })
    assert resp.status_code == 403


def test_admin_can_list_users(client, admin_token):
    resp = client.get("/admin/users", headers={
        "Authorization": f"Bearer {admin_token}",
    })
    assert resp.status_code == 200
    body = resp.json()
    assert body["total"] >= 1
    assert any(u["email"] == "admin@test.local" for u in body["items"])


def test_admin_can_add_a_server(client, admin_token):
    payload = {
        "id": "test-1", "country": "Testland", "city": "Testville",
        "ip_address": "203.0.113.1", "is_premium": False,
        "status": "online", "load_percent": 10,
        "wg_public_key": "dGVzdC1wdWJsaWMta2V5LWJhc2U2NC1wYWRkaW5nPQ==",
        "wg_endpoint": "203.0.113.1:51820",
    }
    resp = client.post("/admin/servers", json=payload, headers={
        "Authorization": f"Bearer {admin_token}",
    })
    assert resp.status_code == 201
    assert resp.json()["id"] == "test-1"

    listed = client.get("/servers")
    assert any(s["id"] == "test-1" for s in listed.json())


def test_normal_user_cannot_add_a_server(client, user_token):
    resp = client.post("/admin/servers", json={
        "id": "evil-1", "country": "X", "city": "Y", "ip_address": "203.0.113.9",
        "is_premium": False, "status": "online", "load_percent": 0,
    }, headers={"Authorization": f"Bearer {user_token}"})
    assert resp.status_code == 403


def test_get_unknown_server_returns_404(client):
    assert client.get("/servers/does-not-exist").status_code == 404
