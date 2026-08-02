from dataclasses import asdict, dataclass
from typing import Any


@dataclass(frozen=True)
class Item:
    id: str
    title: str
    category: str
    dynasty: str
    summary: str
    content: str
    source_url: str
    updated_at: str
    section: str = "资治通鉴"
    volume_id: str | None = None
    year_id: str | None = None
    original: str = ""
    translation: str = ""
    notes: str = ""
    tags: tuple[str, ...] = ()

    def to_dict(self) -> dict[str, Any]:
        payload = asdict(self)
        payload["tags"] = list(self.tags)
        return payload


@dataclass(frozen=True)
class LibrarySection:
    id: str
    title: str
    description: str
    source_url: str
    sort_order: int

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class Volume:
    id: str
    section_id: str
    title: str
    dynasty: str
    sort_order: int

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class ReadingYear:
    id: str
    volume_id: str
    title: str
    era: str
    sort_order: int
    year_int: int | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class KnowledgeEntry:
    id: str
    title: str
    category: str
    summary: str
    content: str
    source_url: str
    updated_at: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)
