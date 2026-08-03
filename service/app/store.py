import json
import sqlite3
import threading
import time
from pathlib import Path
from typing import Any

from .models import Item, KnowledgeEntry, LibrarySection, ReadingYear, Volume


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
        volume_id="zizhi-volume-001",
        year_id="zizhi-year-001",
        original="初命晉大夫魏斯、趙籍、韓虔爲諸侯。",
        translation="周威烈王二十三年，初次任命晋国大夫魏斯、赵籍、韩虔为诸侯。",
        notes="胡三省注和司马光史论可在阅读器中展开。",
        tags=("名分", "三家分晋"),
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
        volume_id="zizhi-volume-001",
        year_id="zizhi-year-001",
        original="周威烈王二十三年。",
        translation="公元前403年，战国历史由此展开。",
        tags=("周纪", "战国"),
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
        volume_id="zizhi-volume-001",
        year_id="zizhi-year-002",
        original="智果曰：赵孟之良，赵氏之利也。",
        translation="智果认为，观察继承人不能只看才能，也要看德行。",
        tags=("人物", "继承"),
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
        volume_id="jishi-volume-001",
        year_id="jishi-year-001",
        original="三家分晋纪事本末。",
        translation="按事件时间线梳理三家分晋的前因后果。",
        tags=("事件", "时间线"),
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
        volume_id="lun-volume-001",
        year_id="lun-year-001",
        original="名分与治道。",
        translation="从史论角度阅读制度与政局演变。",
        tags=("史论", "治道"),
    ),
)

SECTIONS = (
    LibrarySection("zizhi", "资治通鉴", "司马光编年体史书，按卷、纪、年阅读。", "https://www.dutongjian.com/", 1),
    LibrarySection("jishi", "纪事本末", "以事件为纲，重建历史因果与时间线。", "https://www.dutongjian.com/", 2),
    LibrarySection("lun", "读通鉴论", "以通鉴史实为基础的历代成败评论。", "https://www.dutongjian.com/", 3),
    LibrarySection("wiki", "通鉴百科", "人物、战争、地点、政权与典故知识库。", "https://wiki.dutongjian.com/", 4),
)

VOLUMES = (
    Volume("zizhi-volume-001", "zizhi", "卷第一（周纪一）", "周纪", 1),
    Volume("zizhi-volume-002", "zizhi", "卷第二（周纪二）", "周纪", 2),
    Volume("jishi-volume-001", "jishi", "三家分晋", "战国", 1),
    Volume("lun-volume-001", "lun", "读通鉴论卷一", "史论", 1),
)

YEARS = (
    ReadingYear("zizhi-year-001", "zizhi-volume-001", "威烈王二十三年", "公元前403年", 1),
    ReadingYear("zizhi-year-002", "zizhi-volume-001", "威烈王二十四年", "公元前402年", 2),
    ReadingYear("jishi-year-001", "jishi-volume-001", "三家分晋纪事", "战国初年", 1),
    ReadingYear("lun-year-001", "lun-volume-001", "名分与治道", "史论", 1),
)

KNOWLEDGE_ENTRIES = (
    KnowledgeEntry("wiki-three-jin", "三家分晋", "事件", "魏、赵、韩受封为诸侯，标志战国时代展开。", "三家分晋是资治通鉴开篇的重要历史事件，也常作为理解名分、制度和权力转移的入口。", "https://wiki.dutongjian.com/三家分晋", "2026-01-01T00:00:00Z"),
    KnowledgeEntry("wiki-zhaowuxu", "赵无恤", "人物", "赵氏继承与战国政治中的重要人物。", "赵无恤即赵襄子，相关故事涉及识人、继承与代地经营，可从资治通鉴卷一交叉阅读。", "https://wiki.dutongjian.com/趙無恤", "2026-01-01T00:00:00Z"),
    KnowledgeEntry("wiki-xuanwumen", "玄武门之变", "事件", "唐初宫廷政变与太宗即位。", "玄武门之变可与资治通鉴唐纪和读通鉴论相关评论关联阅读。", "https://wiki.dutongjian.com/玄武门之变", "2026-01-01T00:00:00Z"),
    KnowledgeEntry("wiki-song", "宋", "政权", "宋代政治、史学与文化知识条目。", "宋代史学发达，资治通鉴的编修也发生在这一历史背景中。", "https://wiki.dutongjian.com/宋", "2026-01-01T00:00:00Z"),
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
                    source_url TEXT NOT NULL, updated_at TEXT NOT NULL,
                    section TEXT NOT NULL DEFAULT '资治通鉴', volume_id TEXT,
                    year_id TEXT, original TEXT NOT NULL DEFAULT '',
                    translation TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '',
                    tags TEXT NOT NULL DEFAULT '[]', sort_order INTEGER NOT NULL DEFAULT 0
                );
                CREATE TABLE IF NOT EXISTS http_cache (
                    cache_key TEXT PRIMARY KEY, body TEXT NOT NULL,
                    expires_at REAL NOT NULL, content_hash TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS sections (
                    id TEXT PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL,
                    source_url TEXT NOT NULL, sort_order INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS volumes (
                    id TEXT PRIMARY KEY, section_id TEXT NOT NULL, title TEXT NOT NULL,
                    dynasty TEXT NOT NULL, sort_order INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS years (
                    id TEXT PRIMARY KEY, volume_id TEXT NOT NULL, title TEXT NOT NULL,
                    era TEXT NOT NULL, sort_order INTEGER NOT NULL, year_int INTEGER
                );
                CREATE TABLE IF NOT EXISTS knowledge_entries (
                    id TEXT PRIMARY KEY, title TEXT NOT NULL, category TEXT NOT NULL,
                    summary TEXT NOT NULL, content TEXT NOT NULL, source_url TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                """
            )
            existing_columns = {row[1] for row in connection.execute("PRAGMA table_info(items)").fetchall()}
            for name, definition in {
                "section": "TEXT NOT NULL DEFAULT '资治通鉴'",
                "volume_id": "TEXT",
                "year_id": "TEXT",
                "original": "TEXT NOT NULL DEFAULT ''",
                "translation": "TEXT NOT NULL DEFAULT ''",
                "notes": "TEXT NOT NULL DEFAULT ''",
                "tags": "TEXT NOT NULL DEFAULT '[]'",
                "sort_order": "INTEGER NOT NULL DEFAULT 0",
            }.items():
                if name not in existing_columns:
                    connection.execute(f"ALTER TABLE items ADD COLUMN {name} {definition}")
            year_columns = {row[1] for row in connection.execute("PRAGMA table_info(years)").fetchall()}
            if "year_int" not in year_columns:
                connection.execute("ALTER TABLE years ADD COLUMN year_int INTEGER")
            count = connection.execute("SELECT COUNT(*) FROM items").fetchone()[0]
            if count == 0:
                connection.executemany(
                    """INSERT INTO items
                    (id, title, category, dynasty, summary, content, source_url, updated_at,
                     section, volume_id, year_id, original, translation, notes, tags, sort_order)
                    VALUES (:id, :title, :category, :dynasty, :summary, :content, :source_url, :updated_at,
                            :section, :volume_id, :year_id, :original, :translation, :notes, :tags, :sort_order)""",
                    [self._item_values(item) for item in SEED_ITEMS],
                )
            else:
                for item in SEED_ITEMS:
                    values = self._item_values(item)
                    connection.execute(
                        """UPDATE items SET section = :section, volume_id = :volume_id, year_id = :year_id,
                        original = :original, translation = :translation, notes = :notes, tags = :tags,
                        sort_order = :sort_order
                        WHERE id = :id AND (volume_id IS NULL OR year_id IS NULL OR tags = '[]')""",
                        values,
                    )
            connection.executemany("INSERT OR IGNORE INTO sections VALUES (:id, :title, :description, :source_url, :sort_order)", [section.to_dict() for section in SECTIONS])
            connection.executemany("INSERT OR IGNORE INTO volumes VALUES (:id, :section_id, :title, :dynasty, :sort_order)", [volume.to_dict() for volume in VOLUMES])
            connection.executemany(
                """INSERT OR IGNORE INTO years
                (id, volume_id, title, era, sort_order, year_int)
                VALUES (:id, :volume_id, :title, :era, :sort_order, :year_int)""",
                [year.to_dict() for year in YEARS],
            )
            connection.executemany("INSERT OR IGNORE INTO knowledge_entries VALUES (:id, :title, :category, :summary, :content, :source_url, :updated_at)", [entry.to_dict() for entry in KNOWLEDGE_ENTRIES])

    @staticmethod
    def _item_values(item: Item) -> dict[str, Any]:
        values = item.to_dict()
        values["tags"] = json.dumps(values["tags"], ensure_ascii=False)
        return values

    @staticmethod
    def _item(row: sqlite3.Row) -> Item:
        values = dict(row)
        values["tags"] = tuple(json.loads(values.get("tags") or "[]"))
        return Item(**values)

    def list_items(self, category: str | None = None, query: str | None = None, limit: int = 20, year_id: str | None = None) -> list[Item]:
        sql = "SELECT * FROM items"
        values: list[Any] = []
        clauses = []
        if category:
            clauses.append("category = ?")
            values.append(category)
        if query:
            clauses.append("(title LIKE ? OR summary LIKE ? OR content LIKE ? OR original LIKE ? OR translation LIKE ? OR dynasty LIKE ? OR tags LIKE ?)")
            needle = f"%{query.strip()}%"
            values.extend([needle] * 7)
        if year_id:
            clauses.append("year_id = ?")
            values.append(year_id)
        if clauses:
            sql += " WHERE " + " AND ".join(clauses)
        sql += " ORDER BY sort_order ASC, title ASC, id ASC LIMIT ?" if year_id else " ORDER BY updated_at DESC, title LIMIT ?"
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

    def sections(self) -> list[LibrarySection]:
        with self._lock, self._connect() as connection:
            return [LibrarySection(**dict(row)) for row in connection.execute("SELECT * FROM sections ORDER BY sort_order").fetchall()]

    def volumes(self, section_id: str) -> list[Volume]:
        with self._lock, self._connect() as connection:
            return [Volume(**dict(row)) for row in connection.execute("SELECT * FROM volumes WHERE section_id = ? ORDER BY sort_order", (section_id,)).fetchall()]

    def years(self, volume_id: str) -> list[ReadingYear]:
        with self._lock, self._connect() as connection:
            return [ReadingYear(**dict(row)) for row in connection.execute("SELECT * FROM years WHERE volume_id = ? ORDER BY sort_order", (volume_id,)).fetchall()]

    def upsert_volumes(self, volumes: list[Volume]) -> None:
        with self._lock, self._connect() as connection:
            connection.executemany(
                """INSERT OR REPLACE INTO volumes
                (id, section_id, title, dynasty, sort_order)
                VALUES (:id, :section_id, :title, :dynasty, :sort_order)""",
                [volume.to_dict() for volume in volumes],
            )

    def upsert_years(self, years: list[ReadingYear]) -> None:
        with self._lock, self._connect() as connection:
            connection.executemany(
                """INSERT OR REPLACE INTO years
                (id, volume_id, title, era, sort_order, year_int)
                VALUES (:id, :volume_id, :title, :era, :sort_order, :year_int)""",
                [year.to_dict() for year in years],
            )

    def upsert_catalog(self, volumes: list[Volume], years: list[ReadingYear]) -> None:
        """Persist supplemental catalog levels before their content is fetched."""
        with self._lock, self._connect() as connection:
            connection.executemany(
                """INSERT OR REPLACE INTO volumes
                (id, section_id, title, dynasty, sort_order)
                VALUES (:id, :section_id, :title, :dynasty, :sort_order)""",
                [volume.to_dict() for volume in volumes],
            )
            connection.executemany(
                """INSERT OR REPLACE INTO years
                (id, volume_id, title, era, sort_order, year_int)
                VALUES (:id, :volume_id, :title, :era, :sort_order, :year_int)""",
                [year.to_dict() for year in years],
            )

    def count_items(self, category: str | None = None) -> int:
        sql = "SELECT COUNT(*) FROM items"
        values: tuple[Any, ...] = ()
        if category:
            sql += " WHERE category = ?"
            values = (category,)
        with self._lock, self._connect() as connection:
            return int(connection.execute(sql, values).fetchone()[0])

    def count_real_items(self, year_id: str | None = None) -> int:
        """Count imported 通鉴 records without bundled seed content."""
        clauses = ["id LIKE 'zztj-%'", "category = '资治通鉴'"]
        values: tuple[Any, ...] = ()
        if year_id:
            clauses.append("year_id = ?")
            values = (year_id,)
        with self._lock, self._connect() as connection:
            return int(
                connection.execute(
                    f"SELECT COUNT(*) FROM items WHERE {' AND '.join(clauses)}", values
                ).fetchone()[0]
            )

    def remove_seed_items(self, prefix: str = "zizhi-tongjian-") -> None:
        with self._lock, self._connect() as connection:
            connection.execute("DELETE FROM items WHERE id LIKE ?", (f"{prefix}%",))

    def remove_seed_catalog(self) -> None:
        with self._lock, self._connect() as connection:
            connection.execute("DELETE FROM years WHERE id LIKE 'zizhi-year-%'")
            connection.execute("DELETE FROM volumes WHERE id LIKE 'zizhi-volume-%'")

    def clear_tongjian_content(self) -> None:
        """Remove the imported corpus and its hierarchy before a clean resync."""
        with self._lock, self._connect() as connection:
            connection.execute("DELETE FROM items WHERE category = '资治通鉴' OR id LIKE 'zztj-%'")
            connection.execute("DELETE FROM years WHERE volume_id IN (SELECT id FROM volumes WHERE section_id = 'zizhi')")
            connection.execute("DELETE FROM volumes WHERE section_id = 'zizhi'")
            connection.execute("DELETE FROM knowledge_entries")

    def knowledge(self, category: str | None = None, query: str | None = None, limit: int = 20) -> list[KnowledgeEntry]:
        sql = "SELECT * FROM knowledge_entries"
        values: list[Any] = []
        clauses = []
        if category:
            clauses.append("category = ?")
            values.append(category)
        if query:
            clauses.append("(title LIKE ? OR summary LIKE ? OR content LIKE ?)")
            needle = f"%{query.strip()}%"
            values.extend([needle] * 3)
        if clauses:
            sql += " WHERE " + " AND ".join(clauses)
        sql += " ORDER BY updated_at DESC, title LIMIT ?"
        values.append(limit)
        with self._lock, self._connect() as connection:
            return [KnowledgeEntry(**dict(row)) for row in connection.execute(sql, values).fetchall()]

    def get_knowledge(self, entry_id: str) -> KnowledgeEntry | None:
        with self._lock, self._connect() as connection:
            row = connection.execute("SELECT * FROM knowledge_entries WHERE id = ?", (entry_id,)).fetchone()
            return KnowledgeEntry(**dict(row)) if row else None

    def upsert_knowledge(self, entries: list[KnowledgeEntry]) -> None:
        with self._lock, self._connect() as connection:
            connection.executemany(
                """INSERT OR REPLACE INTO knowledge_entries
                (id, title, category, summary, content, source_url, updated_at)
                VALUES (:id, :title, :category, :summary, :content, :source_url, :updated_at)""",
                [entry.to_dict() for entry in entries],
            )

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
            connection.executemany(
                """INSERT OR REPLACE INTO items
                (id, title, category, dynasty, summary, content, source_url, updated_at,
                 section, volume_id, year_id, original, translation, notes, tags, sort_order)
                VALUES (:id, :title, :category, :dynasty, :summary, :content, :source_url, :updated_at,
                        :section, :volume_id, :year_id, :original, :translation, :notes, :tags, :sort_order)""",
                [self._item_values(item) for item in items],
            )
