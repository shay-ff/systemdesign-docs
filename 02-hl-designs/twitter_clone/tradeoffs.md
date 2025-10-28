# Trade-offs

- Fan-out on write vs fan-out on read: choose based on read/write ratio.
- Use denormalized timelines in cache for low-latency reads at cost of complexity on write.
