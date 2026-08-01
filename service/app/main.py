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


@app.get("/api/home")
def home() -> dict[str, Any]:
    return envelope({"items": item_list(store.list_items(limit=12)), "categories": store.categories()})


@app.get("/api/search")
def search(q: str = Query(min_length=1, max_length=80), limit: int = Query(default=20, ge=1, le=50)) -> dict[str, Any]:
    return envelope({"query": q.strip(), "items": item_list(store.list_items(query=q.strip(), limit=limit))})


@app.get("/api/items")
def items(category: str | None = None, limit: int = Query(default=20, ge=1, le=50)) -> dict[str, Any]:
    return envelope({"category": category, "items": item_list(store.list_items(category=category, limit=limit))})


@app.get("/api/detail/{item_id}", response_model=None)
def detail(item_id: str) -> JSONResponse | dict[str, Any]:
    item = store.get_item(item_id)
    if item is None:
        return JSONResponse(status_code=404, content=envelope(None, "item not found", 404))
    return envelope(item.to_dict())
