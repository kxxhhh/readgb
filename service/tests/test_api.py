import pytest
from httpx import ASGITransport, AsyncClient

from app.main import app




@pytest.fixture
async def client():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://testserver") as async_client:
        yield async_client


@pytest.mark.anyio
async def test_home_uses_uniform_response_envelope(client):
    response = await client.get("/api/home")

    assert response.status_code == 200
    payload = response.json()
    assert payload["code"] == 0
    assert payload["message"] == "success"
    assert payload["data"]["items"]
    assert "categories" in payload["data"]


@pytest.mark.anyio
async def test_search_is_case_insensitive_and_trimmed(client):
    response = await client.get("/api/search", params={"q": "  周威烈王  "})

    assert response.status_code == 200
    items = response.json()["data"]["items"]
    assert items
    assert any("周威烈王" in item["title"] for item in items)


@pytest.mark.anyio
async def test_search_rejects_whitespace_only_query(client):
    response = await client.get("/api/search", params={"q": "  "})

    assert response.status_code == 422
    assert response.json()["message"] == "query must not be blank"


@pytest.mark.anyio
async def test_detail_returns_uniform_not_found_response(client):
    response = await client.get("/api/detail/missing-item")

    assert response.status_code == 404
    assert response.json() == {"code": 404, "message": "item not found", "data": None}


@pytest.mark.anyio
async def test_items_can_filter_by_category(client):
    response = await client.get("/api/items", params={"category": "资治通鉴"})

    assert response.status_code == 200
    assert all(item["category"] == "资治通鉴" for item in response.json()["data"]["items"])


@pytest.mark.anyio
async def test_catalog_exposes_section_volume_year_hierarchy(client):
    sections = await client.get("/api/sections")
    assert sections.status_code == 200
    section = next(item for item in sections.json()["data"]["sections"] if item["id"] == "zizhi")

    volumes = await client.get(f"/api/sections/{section['id']}/volumes")
    assert volumes.status_code == 200
    volume_id = volumes.json()["data"]["volumes"][0]["id"]

    years = await client.get(f"/api/volumes/{volume_id}/years")
    assert years.status_code == 200
    year_id = years.json()["data"]["years"][0]["id"]

    items = await client.get(f"/api/years/{year_id}/items")
    assert items.status_code == 200
    assert items.json()["data"]["items"]


@pytest.mark.anyio
async def test_knowledge_supports_category_search_and_detail(client):
    response = await client.get("/api/knowledge", params={"q": "赵无恤"})

    assert response.status_code == 200
    entry = response.json()["data"]["items"][0]
    assert entry["category"] == "人物"

    detail = await client.get(f"/api/knowledge/{entry['id']}")
    assert detail.status_code == 200
    assert detail.json()["data"]["title"] == "赵无恤"
