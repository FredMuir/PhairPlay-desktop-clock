# Third-party notices

## OkHttp 4.12.0

PhairPlay uses the OkHttp client from [square/okhttp](https://github.com/square/okhttp/tree/parent-4.12.0) for direct CalDAV communication. The dependency is used for standard WebDAV `PROPFIND` and CalDAV `REPORT` requests that Android's `HttpURLConnection` does not expose as supported request methods. No OkHttp source has been copied or modified.

License: [Apache License 2.0](https://github.com/square/okhttp/blob/parent-4.12.0/LICENSE.txt).

OkHttp 4.12.0 brings Okio 3.6.0 transitively; Okio is also licensed under Apache License 2.0. Existing Kotlin runtime dependencies are reused.
