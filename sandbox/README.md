# Isolated coding execution

The backend now enqueues durable SAMPLE/GRADE jobs and connects to the executable [gateway](gateway/server.mjs). The gateway creates disposable gVisor Kubernetes Jobs, one test input per sandbox. It does not receive expected outputs; deterministic comparison happens in the application worker. Candidate routes only expose SAMPLE results. Hidden grading returns counts and weighted scores without test inputs/outputs.

## Deployment prerequisites

Use a **separate reviewed Kubernetes cluster/worker pool**, not an application host. Install and verify the `gvisor` RuntimeClass and a CNI enforcing NetworkPolicy, including metadata endpoint/IPv6 denial. Set kubelet `podPidsLimit=64`, bounded container log rotation and node ephemeral storage limits. Registry access is by node identity only; no credentials enter guest pods. Benchmark resource accounting and validate kernel/runtime/escape controls before candidate use. Kubernetes API TLS validation is required.

1. Build runtime from `runtime/Dockerfile` in a trusted build environment, scan it, publish to your approved registry and record its SHA-256 digest. The image tag alone is rejected by gateway policy.
2. Apply `kubernetes.yaml` on the sandbox cluster. It provisions restricted namespaces, default-deny execution networking, quotas, limited gateway RBAC and mandatory gVisor/digest admission. It intentionally does not install or substitute a runtime.
3. Deploy gateway image built from `gateway/Dockerfile` in namespace `assessment-runner` using service account `execution-gateway`, with `RUNNER_IMAGE=registry/image@sha256:...`, `EXECUTION_NAMESPACE=assessment-sandbox` and a random `RUNNER_TOKEN` of at least 32 characters from a secret store. One replica/request at a time, at most four concurrent guest pods per request.
4. Put the gateway behind private HTTPS ingress restricted to backend worker egress. Set backend RUNNER_URL and matching secret RUNNER_TOKEN. Neither value can be supplied by API clients.
5. Run the contract tests, actual compile/runtime/wrong-answer/time/memory/output/fork/egress/escape tests and reference/mutation tests before enabling coding assessments. No real sandbox execution has yet been verified in this workspace.

Request fields: `jobId,language,source,inputs,timeoutMs,memoryMb`. Supported languages: Java 21, JavaScript (runtime-image Node version), Python 3. Unknown fields/commands/endpoints/languages and out-of-range limits are rejected. Response: `{cases:[{status,stdout}]}`. Authenticated gateway failures are infrastructure failures and never false zeroes. Responses are bounded by both gateway and backend.

Sample requests require a live attempt/tab lease and the exact acknowledged answer revision. Grade requests pin the final attempt revision. Workers use row claims, fencing tokens and bounded retries; late duplicate responses cannot replace results. Worker outage does not block save/heartbeat/submit. Infrastructure exhaustion leaves evaluation pending for operational recovery. New coding publications require hidden tests. Historic versions without tests remain pending, not silently rewritten.

## Local checks

```sh
node --test sandbox/gateway/policy.test.mjs
node --check sandbox/gateway/server.mjs
node --check sandbox/runtime/execute.mjs
```

**Do not run execute.mjs on the host.** It is an image entrypoint for the sandbox only. Compose does not fall back to ordinary Docker execution. Full live isolation validation, node policies, secure gateway TLS deployment and runtime image approval are remaining deployment gates.
