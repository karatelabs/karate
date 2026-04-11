# TODO

- [ ] Add SSE browser-side test to `DriverFeatureTest` — verify `EventSource` connects to `SseHandler` and receives events in a real browser. Current SSE tests only validate server-side wire format. This would cover the HTMX `sse-swap` and Alpine `EventSource` patterns end-to-end.
