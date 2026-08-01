import hashlib
import time
from collections.abc import Callable
from urllib.error import URLError
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen
from urllib.robotparser import RobotFileParser


class RobotsAwareFetcher:
    def __init__(
        self,
        base_url: str,
        *,
        opener: Callable = urlopen,
        robots_checker: Callable[[str], bool] | None = None,
        sleep: Callable[[float], None] = time.sleep,
        retries: int = 3,
        min_interval: float = 1.0,
        timeout: float = 15.0,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.opener = opener
        self._robots_checker = robots_checker
        self.sleep = sleep
        self.retries = retries
        self.min_interval = min_interval
        self.timeout = timeout
        self._last_request = 0.0
        self._cache: dict[str, str] = {}
        self._robots: RobotFileParser | None = None

    def _allowed(self, url: str) -> bool:
        if self._robots_checker is not None:
            return self._robots_checker(url)
        if self._robots is None:
            self._robots = RobotFileParser(urljoin(f"{self.base_url}/", "robots.txt"))
            try:
                self._robots.read()
            except (OSError, URLError):
                return False
        return self._robots.can_fetch("dutongjian-app/1.0", url)

    def fetch(self, path: str) -> str | None:
        url = path if path.startswith("http") else urljoin(f"{self.base_url}/", path.lstrip("/"))
        if urlparse(url).netloc != urlparse(self.base_url).netloc or not self._allowed(url):
            return None
        if url in self._cache:
            return self._cache[url]
        for attempt in range(self.retries + 1):
            elapsed = time.monotonic() - self._last_request
            if elapsed < self.min_interval:
                self.sleep(self.min_interval - elapsed)
            try:
                request = Request(url, headers={"User-Agent": "dutongjian-app/1.0 (+local development)"})
                with self.opener(request, timeout=self.timeout) as response:
                    body = response.read().decode("utf-8", errors="replace")
                self._last_request = time.monotonic()
                self._cache[url] = body
                hashlib.sha256(body.encode("utf-8")).hexdigest()
                return body
            except (OSError, URLError):
                if attempt == self.retries:
                    return None
                self.sleep(min(30.0, 2**attempt))
        return None
