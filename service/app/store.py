import json
import sqlite3
import threading
import time
from pathlib import Path
from typing import Any

from .models import Item


SEED_ITEMS = (
    Item(
        id="zizhi-tongjian-001",
        title="三家分晋",
        category="资治通鉴",
        dynasty="周纪",
        summary="周威烈王二十三年，晋国大夫魏斯、赵籍、韩虔受封为诸侯。",
        content="臣光曰：天子的职责没有比尊崇礼制更重要的，尊崇礼制没有比匡正名分更重要的。",
        source_url="https://www.dutongjian.com/",
        updated_at="2026-01-01T00:00:00Z",
    ),
    Item(
        id="zizhi-tongjian-002",
        title="周威烈王二十三年",
        category="资治通鉴",
        dynasty="周纪一",
        summary="卷第一，记录战国开端的重要政局变化。",
        content="周威烈王二十三年（公元前403年），晋国三家分立，历史进入新的阶段。",
        source_url="https://www.dutongjian.com/",
        updated_at="2026-01-01T00:00:00Z",
    ),
    Item(
        id="zizhi-tongjian-003",
        title="智瑶与智氏",
        category="资治通鉴",
        dynasty="周纪一",
        summary="从继承人选择观察才与德的关系。",
        content="智果认为智瑶有五项长处，却有不仁这一项短处，不能只以才能判断继承人。",
        source_url="https://www.dutongjian.com/",
        updated_at="2026-01-01T00:00:00Z",
    ),
    Item(
        id="tongjian-jishi-001",
        title="三家分晋纪事本末",
        category="通鉴纪事本末",
        dynasty="战国",
        summary="以事件为纲，梳理三家分晋的前因后果。",
        content="本条目按事件组织相关史料，便于从时间线理解历史决策。",
        source_url="https://www.dutongjian.com/",
        updated_at="2026-01-01T00:00:00Z",
    ),
    Item(
        id="dutongjian-lun-001",
        title="读通鉴论：名分与治道",
        category="读通鉴论",
        dynasty="史论",
        summary="从史论角度阅读名分、制度与政局演变。",
        content="读通鉴论以通鉴所载史实评论历代成败兴亡，适合与原文对照阅读。",
        source_url="https://www.dutongjian.com/",
        updated_at="2026-01-01T00:00:00Z",
    ),
)


class ContentStore:
    """SQLite 内容索引与 HTTP 缓存，默认单进程安全。"""

    def __init__(self, path: str | Path = "data/dutongjian.db") -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self._initialize()

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.path, check_same_thread=False)
        connection.row_factory = sqlite3.Row
        return connection

    def _initialize(self) -> None:
        with self._lock, self._connect() as connection:
            connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS items (
                    id TEXT PRIMARY KEY, title TEXT NOT NULL, category TEXT NOT NULL,
                    dynasty TEXT NOT NULL, summary TEXT NOT NULL, content TEXT NOT NULL,
                    source_url TEXT NOT NULL, updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS http_cache (
                    cache_key TEXT PRIMARY KEY, body TEXT NOT NULL,
                    expires_at REAL NOT NULL, content_hash TEXT NOT NULL
                );
                """
            )
            count = connection.execute("SELECT COUNT(*) FROM items").fetchone()[0]
            if count == 0:
                connection.executemany(
                    "INSERT INTO items VALUES (:id, :title, :category, :dynasty, :summary, :content, :source_url, :updated_at)",
                    [item.to_dict() for item in SEED_ITEMS],
                )

    @staticmethod
    def _item(row: sqlite3.Row) -> Item:
        return Item(**dict(row))

    def list_items(self, category: str | None = None, query: str | None = None, limit: int = 20) -> list[Item]:
        sql = "SELECT * FROM items"
        values: list[Any] = []
        clauses = []
        if category:
            clauses.append("category = ?")
            values.append(category)
        if query:
            clauses.append("(title LIKE ? OR summary LIKE ? OR content LIKE ? OR dynasty LIKE ?)")
            needle = f"%{query.strip()}%"
            values.extend([needle] * 4)
        if clauses:
            sql += " WHERE " + " AND ".join(clauses)
        sql += " ORDER BY updated_at DESC, title LIMIT ?"
        values.append(limit)
        with self._lock, self._connect() as connection:
            return [self._item(row) for row in connection.execute(sql, values).fetchall()]

    def get_item(self, item_id: str) -> Item | None:
        with self._lock, self._connect() as connection:
            row = connection.execute("SELECT * FROM items WHERE id = ?", (item_id,)).fetchone()
            return self._item(row) if row else None

    def categories(self) -> list[str]:
        with self._lock, self._connect() as connection:
            return [row[0] for row in connection.execute("SELECT DISTINCT category FROM items ORDER BY category").fetchall()]

    def get_cache(self, key: str) -> str | None:
        with self._lock, self._connect() as connection:
            row = connection.execute("SELECT body, expires_at FROM http_cache WHERE cache_key = ?", (key,)).fetchone()
            if not row:
                return None
            if row[1] <= time.time():
                connection.execute("DELETE FROM http_cache WHERE cache_key = ?", (key,))
                return None
            return row[0]

    def put_cache(self, key: str, body: str, content_hash: str, ttl_seconds: int) -> None:
        with self._lock, self._connect() as connection:
            connection.execute(
                "INSERT OR REPLACE INTO http_cache VALUES (?, ?, ?, ?)",
                (key, body, time.time() + ttl_seconds, content_hash),
            )

    def upsert_items(self, items: list[Item]) -> None:
        with self._lock, self._connect() as connection:
            connection.executemany("INSERT OR REPLACE INTO items VALUES (:id, :title, :category, :dynasty, :summary, :content, :source_url, :updated_at)", [item.to_dict() for item in items])
