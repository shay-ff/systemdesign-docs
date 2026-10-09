# URL Shortener — Design Explanation

This implementation demonstrates the core URL-shortening flow in a deliberately
small, in-memory C++ class. It shows how an opaque short code maps to the
original URL before persistence, concurrency, and distributed scaling are added.

## The `URLShortener` class

`URLShortener` owns the state required by the example:

- `db` maps a numeric ID to the original URL.
- `counter` supplies the next numeric ID.

Shortening allocates an ID and stores the mapping. Expanding decodes a code,
looks up the mapping, and returns the original URL.

## Shortening a URL

`shorten` uses a monotonic counter rather than hashing the URL:

1. Read the current counter value.
2. Increment the counter for the next request.
3. Store the original URL under that ID.
4. Encode the ID as a Base62 string.

The counter gives deterministic uniqueness within this process. Identical long
URLs receive different codes because each shortening request creates a new link.

## Base62 encoding

Base62 uses `0-9`, `a-z`, and `A-Z`, giving 62 possible characters per
position. The encoder repeatedly takes `num % 62`, converts the remainder to a
character, divides by 62, and reverses the collected characters.

This keeps the visible code shorter than a decimal ID. The decoder performs the
inverse operation by multiplying the accumulated value by 62 and adding each
character's position in the alphabet.

## Expanding a URL

`expand` decodes the short code to an ID and checks `db`. If the ID exists, it
returns the stored URL. If it does not exist, it returns an empty string.

The `unordered_map` lookup is expected to be O(1) on average. A web service
should return an explicit not-found response instead of treating an empty
string as a valid URL.

## Complexity

| Operation | Average time | Space |
|---|---:|---:|
| Shorten | O(log₆₂ ID) | O(1) additional |
| Expand | O(log₆₂ ID) | O(1) additional |
| Store all mappings | — | O(n) |

The complete in-memory state grows linearly with the number of shortened URLs.

## Important limitations

This is an educational single-process implementation, not a production service:

- Mappings disappear when the process exits.
- A single counter cannot safely generate IDs across multiple instances.
- The class is not thread-safe.
- Invalid characters are not rejected by `decode_base62`.
- There is no URL validation, abuse protection, expiration, ownership, or analytics.
- An empty string does not distinguish an unknown code from other errors.

## Production extensions

A production design can keep the same `shorten` and `expand` boundary while
replacing the implementation details:

1. Persist mappings in a database and cache redirect reads.
2. Allocate IDs through a database sequence or coordination-safe ID service.
3. Validate destination URLs and apply abuse and malware checks.
4. Return explicit results for malformed and unknown codes.
5. Add synchronization or use an external store for concurrent instances.
6. Store expiration, owner, custom alias, and click-event metadata.

These concerns belong to the high-level design; this low-level example focuses
on reversible Base62 encoding and an ID-to-URL map.
