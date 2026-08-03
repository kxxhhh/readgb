import os
from typing import Any

from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .store import ContentStore


app = FastAPI(title="读通鉴数据服务", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("CORS_ORIGINS", "*").split(","),
    allow_methods=["GET"],
    allow_headers=["*"],
)
store = ContentStore(os.getenv("DUTONGJIAN_DB", "data/dutongjian.db"))


def envelope(data: Any, message: str = "success", code: int = 0) -> dict[str, Any]:
    return {"code": code, "message": message, "data": data}


def item_list(items) -> list[dict[str, Any]]:
    return [item.to_dict() for item in items]


def model_list(models) -> list[dict[str, Any]]:
    return [model.to_dict() for model in models]


@app.get("/api/home")
async def home() -> dict[str, Any]:
    return envelope({
        "items": item_list(store.list_items(limit=12)),
        "categories": store.categories(),
        "sections": model_list(store.sections()),
    })


@app.get("/api/search", response_model=None)
async def search(q: str = Query(min_length=1, max_length=80), limit: int = Query(default=20, ge=1, le=50)) -> JSONResponse | dict[str, Any]:
    query = q.strip()
    if not query:
        return JSONResponse(status_code=422, content=envelope(None, "query must not be blank", 422))
    return envelope({"query": query, "items": item_list(store.list_items(query=query, limit=limit))})


@app.get("/api/items")
async def items(category: str | None = None, year_id: str | None = None, limit: int = Query(default=20, ge=1, le=50)) -> dict[str, Any]:
    return envelope({"category": category, "year_id": year_id, "items": item_list(store.list_items(category=category, year_id=year_id, limit=limit))})


@app.get("/api/detail/{item_id}", response_model=None)
async def detail(item_id: str) -> JSONResponse | dict[str, Any]:
    item = store.get_item(item_id)
    if item is None:
        return JSONResponse(status_code=404, content=envelope(None, "item not found", 404))
    return envelope(item.to_dict())


@app.get("/api/sections")
async def sections() -> dict[str, Any]:
    return envelope({"sections": model_list(store.sections())})


@app.get("/api/sections/{section_id}/volumes")
async def volumes(section_id: str) -> dict[str, Any]:
    return envelope({"section_id": section_id, "volumes": model_list(store.volumes(section_id))})


@app.get("/api/volumes/{volume_id}/years")
async def years(volume_id: str) -> dict[str, Any]:
    return envelope({"volume_id": volume_id, "years": model_list(store.years(volume_id))})


@app.get("/api/years/{year_id}/items")
async def year_items(year_id: str, limit: int = Query(default=50, ge=1, le=100)) -> dict[str, Any]:
    return envelope({"year_id": year_id, "items": item_list(store.list_items(year_id=year_id, limit=limit))})


@app.get("/api/knowledge")
async def knowledge(category: str | None = None, q: str | None = Query(default=None, min_length=1, max_length=80), limit: int = Query(default=20, ge=1, le=50)) -> dict[str, Any]:
    entries = store.knowledge(category=category, query=q, limit=limit)
    categories = sorted({entry.category for entry in store.knowledge(limit=200)})
    return envelope({"category": category, "query": q, "items": model_list(entries), "categories": categories})


@app.get("/api/knowledge/{entry_id}", response_model=None)
async def knowledge_detail(entry_id: str) -> JSONResponse | dict[str, Any]:
    entry = store.get_knowledge(entry_id)
    if entry is None:
        return JSONResponse(status_code=404, content=envelope(None, "knowledge entry not found", 404))
    return envelope(entry.to_dict())
