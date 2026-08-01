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

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)
