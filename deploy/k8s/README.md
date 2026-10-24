# SpecTrace on k3s with a backend HorizontalPodAutoscaler

S4 scalability demonstration. One MySQL StatefulSet (PVC), a stateless backend Deployment
scaled by an `autoscaling/v2` HPA on CPU (min 1, max 5, target 60 % of the 500m request,
scale-down window 60 s for the demo), and the nginx frontend that proxies `/api/` to the
`backend` Service, which load-balances over every ready replica.

| Overlay | Where | Access |
|---|---|---|
| `overlays/local` | Colima k3s on a laptop (`colima start --kubernetes`) | `kubectl port-forward` on 127.0.0.1 only |
| `overlays/server` | teammate's single-node k3s | frontend NodePort `127.0.0.1:30080`, behind the host nginx (TLS + basic auth) for `https://spectrace.52zzx.top` |

Both use locally built images (`spectrace-backend:local`, `spectrace-frontend:local`,
`imagePullPolicy: Never`); nothing is pushed to a registry. The MySQL password is a random
Secret created by the script and never committed.

```bash
scripts/s4-autoscale-demo.sh demo      # images + deploy + k6 ramp + HPA timeline (local)
scripts/s4-autoscale-demo.sh watch     # second terminal: HPA and backend pods every 15 s
scripts/s4-autoscale-demo.sh delete    # removes the namespace and its MySQL data (asks first)
```

Server deployment steps and the recorded results are in
[`docs/evidence/S4-performance-and-autoscaling.md`](../../docs/evidence/S4-performance-and-autoscaling.md).
