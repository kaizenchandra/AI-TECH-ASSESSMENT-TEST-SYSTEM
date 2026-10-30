import {
  Monitor,
  ShieldCheck,
  Cookie,
  Wifi,
  Maximize2,
  Camera,
  Mic,
  AudioLines,
  Volume2,
  RefreshCw,
  CheckCircle2,
  AlertCircle,
  Loader2,
  MinusCircle,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { api, Content } from "./api";
export function Readiness({
  assessment,
  onChange,
  exceptions = [],
}: {
  assessment: Content;
  onChange: (checks: Record<string, boolean>) => void;
  exceptions?: string[];
}) {
  const [checks, setChecks] = useState<Record<string, boolean>>({}),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [latency, setLatency] = useState<number | null>(null),
    [level, setLevel] = useState(0);
  const video = useRef<HTMLVideoElement>(null),
    streams = useRef<MediaStream[]>([]),
    audio = useRef<AudioContext | null>(null),
    frame = useRef(0);
  const generation = useRef(0);
  const latest = useRef<Record<string, boolean>>({});
  const expiry = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  function stop() {
    generation.current++;
    streams.current.forEach((s) => s.getTracks().forEach((t) => t.stop()));
    streams.current = [];
    cancelAnimationFrame(frame.current);
    void audio.current?.close();
    audio.current = null;
  }
  useEffect(() => {
    const devicesChanged = () => {
      stop();
      setBusy(false);
      publish({
        ...latest.current,
        camera: !assessment.cameraRequired,
        microphone: !assessment.microphoneRequired,
        microphoneLevel: !assessment.microphoneRequired,
        playback: !assessment.microphoneRequired,
      });
      setMessage(
        "Devices changed. Repeat the required media checks before starting.",
      );
    };
    navigator.mediaDevices?.addEventListener?.("devicechange", devicesChanged);
    return () => {
      stop();
      clearTimeout(expiry.current);
      navigator.mediaDevices?.removeEventListener?.(
        "devicechange",
        devicesChanged,
      );
    };
  }, [assessment.cameraRequired, assessment.microphoneRequired]);
  function publish(next: Record<string, boolean>) {
    latest.current = next;
    setChecks(next);
    onChange(next);
  }
  async function run() {
    stop();
    clearTimeout(expiry.current);
    const current = generation.current;
    setBusy(true);
    setMessage("");
    const next: Record<string, boolean> = {
      browser: !!window.crypto?.subtle,
      cookies: navigator.cookieEnabled,
      network: false,
      secureContext: window.isSecureContext,
      viewport: innerWidth >= 900,
      camera: !assessment.cameraRequired,
      microphone: !assessment.microphoneRequired,
      microphoneLevel: !assessment.microphoneRequired,
      playback: !assessment.microphoneRequired,
    };
    publish(next);
    try {
      const started = performance.now();
      await api("/me");
      if (current !== generation.current) return;
      setLatency(Math.round(performance.now() - started));
      next.network = true;
    } catch {
      if (current !== generation.current) return;
      setMessage(
        "Check your connection and sign in again if your session expired.",
      );
    }
    for (const kind of ["camera", "microphone"] as const) {
      if (current !== generation.current) return;
      if (
        !assessment[
          kind === "camera" ? "cameraRequired" : "microphoneRequired"
        ] ||
        exceptions.includes(kind)
      )
        continue;
      try {
        const stream = await navigator.mediaDevices.getUserMedia(
          kind === "camera" ? { video: true } : { audio: true },
        );
        if (current !== generation.current) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streams.current.push(stream);
        next[kind] = true;
        stream.getTracks().forEach((t) =>
          t.addEventListener("ended", () => {
            if (current !== generation.current) return;
            next[kind] = false;
            publish({
              ...latest.current,
              [kind]: false,
              ...(kind === "microphone" ? { microphoneLevel: false } : {}),
            });
            setMessage("Device disconnected. Run the checks again.");
          }),
        );
        if (kind === "camera" && video.current) {
          video.current.srcObject = stream;
          await video.current.play();
        }
        if (kind === "microphone") {
          const ctx = new AudioContext();
          audio.current = ctx;
          await ctx.resume();
          if (current !== generation.current) return;
          const analyser = ctx.createAnalyser();
          ctx.createMediaStreamSource(stream).connect(analyser);
          const samples = new Uint8Array(analyser.fftSize);
          function sample() {
            if (current !== generation.current) return;
            analyser.getByteTimeDomainData(samples);
            const peak =
              Math.max(...samples.map((x) => Math.abs(x - 128))) / 128;
            setLevel(peak);
            if (peak > 0.03 && !next.microphoneLevel) {
              next.microphoneLevel = true;
              publish({
                ...latest.current,
                microphone: true,
                microphoneLevel: true,
              });
            }
            frame.current = requestAnimationFrame(sample);
          }
          sample();
        }
      } catch {
        if (current !== generation.current) return;
        next[kind] = false;
        setMessage(
          "Allow device permission, close applications using the device, and retry. Contact your recruiter for an accommodation.",
        );
      }
    }
    if (current !== generation.current) return;
    publish({
      ...next,
      microphoneLevel: next.microphoneLevel || !!latest.current.microphoneLevel,
      playback: next.playback || !!latest.current.playback,
    });
    setBusy(false);
    expiry.current = setTimeout(() => {
      stop();
      publish({});
      setMessage(
        "Readiness expired. Run system checks again; your assessment has not started.",
      );
    }, 600000);
  }
  async function playback() {
    try {
      const ctx = audio.current ?? new AudioContext();
      audio.current = ctx;
      await ctx.resume();
      const tone = ctx.createOscillator(),
        gain = ctx.createGain();
      gain.gain.value = 0.08;
      tone.connect(gain).connect(ctx.destination);
      tone.start();
      tone.stop(ctx.currentTime + 0.4);
    } catch {
      setMessage("Audio playback unavailable. Check output settings.");
    }
  }
  const definitions = [
    {
      key: "browser",
      name: "Browser support",
      icon: Monitor,
      help: "Use an up-to-date desktop browser with JavaScript enabled.",
    },
    {
      key: "secureContext",
      name: "Secure connection",
      icon: ShieldCheck,
      help: "Open the secure HTTPS assessment link supplied by your recruiter.",
    },
    {
      key: "cookies",
      name: "Session & cookies",
      icon: Cookie,
      help: "Allow this site's cookies and sign in again if needed.",
    },
    {
      key: "network",
      name: "Network connection",
      icon: Wifi,
      help: "Reconnect to the internet and verify that you are still signed in.",
    },
    {
      key: "viewport",
      name: "Screen size",
      icon: Maximize2,
      help: "Use a desktop window at least 900 pixels wide. Maximize your browser or reduce zoom.",
    },
    {
      key: "camera",
      name: "Webcam",
      icon: Camera,
      help: "Allow camera access in browser settings and close other apps using it.",
    },
    {
      key: "microphone",
      name: "Microphone",
      icon: Mic,
      help: "Allow microphone access and check the selected input device.",
    },
    {
      key: "microphoneLevel",
      name: "Microphone input",
      icon: AudioLines,
      help: "Speak briefly, unmute your microphone and check its input level.",
    },
    {
      key: "playback",
      name: "Audio playback",
      icon: Volume2,
      help: "Play the test sound, check your output device and confirm you heard it.",
    },
  ];
  const status = (key: string) =>
    exceptions.includes(key)
      ? "exception"
      : (key === "camera" && !assessment.cameraRequired) ||
          (["microphone", "microphoneLevel", "playback"].includes(key) &&
            !assessment.microphoneRequired)
        ? "optional"
        : busy
          ? "checking"
          : checks[key] === undefined
            ? "pending"
            : checks[key]
              ? "passed"
              : "failed";
  const passed = definitions.filter((d) =>
    ["passed", "exception"].includes(status(d.key)),
  ).length;
  const required = definitions.filter(
    (d) => status(d.key) !== "optional",
  ).length;
  const failed = definitions.filter((d) => status(d.key) === "failed").length;
  const labels: Record<string, string> = {
    exception: "Authorized exception",
    optional: "Not required",
    checking: "Checking",
    pending: "Not checked",
    passed: "Passed",
    failed: "Failed",
  };
  return (
    <section
      className="readiness-dashboard"
      aria-label="System readiness checks"
    >
      <div className="readiness-toolbar">
        <div>
          <p className="muted">
            Local checks only. Nothing is recorded or uploaded. Results expire
            after ten minutes.
          </p>
          <span
            className={`readiness-summary ${failed ? "has-failures" : passed === required ? "all-passed" : ""}`}
          >
            <span className="status-dot" />
            {busy
              ? "Checking your setup…"
              : `${passed} of ${required} required checks complete`}
            {failed > 0 ? ` · ${failed} need attention` : ""}
          </span>
        </div>
        <button type="button" disabled={busy} onClick={() => void run()}>
          <RefreshCw size={16} className={busy ? "check-spinner" : ""} />
          {busy ? "Checking…" : "Run system checks"}
        </button>
      </div>
      <div className="readiness-grid">
        {definitions.map(({ key, name, icon: Icon, help }) => {
          const state = status(key);
          const StateIcon =
            state === "passed"
              ? CheckCircle2
              : state === "failed"
                ? AlertCircle
                : state === "checking"
                  ? Loader2
                  : state === "exception"
                    ? ShieldCheck
                    : MinusCircle;
          return (
            <article
              key={key}
              className={`readiness-check is-${state}`}
              aria-label={`${name}: ${labels[state]}`}
            >
              <div className="check-card-top">
                <span className="check-device-icon">
                  <Icon size={20} />
                </span>
                <span className="check-badge">
                  <StateIcon
                    size={14}
                    className={state === "checking" ? "check-spinner" : ""}
                  />
                  {labels[state]}
                </span>
              </div>
              <h4>{name}</h4>
              <p>
                {state === "failed"
                  ? help
                  : state === "passed"
                    ? key === "network" && latency !== null
                      ? `Authenticated round trip: ${latency} ms. Connection quality may change.`
                      : "This check is complete."
                    : state === "optional"
                      ? "Not required for this assessment."
                      : state === "exception"
                        ? "Your recruiter has authorized an alternative."
                        : state === "checking"
                          ? "Checking your browser or device…"
                          : "Run the checks when you are ready."}
              </p>
              {state === "failed" && (
                <button
                  type="button"
                  className="text-button check-retry"
                  aria-label={`Retry ${name}`}
                  disabled={busy}
                  onClick={() => void run()}
                >
                  <RefreshCw size={13} />
                  Retry checks
                </button>
              )}
            </article>
          );
        })}
      </div>
      {(assessment.cameraRequired || assessment.microphoneRequired) && (
        <div className="media-checks">
          {assessment.cameraRequired && (
            <div className="media-card">
              <div className="section-title">
                <Camera size={18} />
                <h4>Local camera preview</h4>
              </div>
              <div className="camera-preview">
                <video
                  ref={video}
                  muted
                  playsInline
                  aria-label="Local webcam preview"
                />
                {!checks.camera && (
                  <span>
                    <Camera size={28} />
                    Your preview appears after permission is granted
                  </span>
                )}
              </div>
              <small>
                Visible only on this device. No recording or upload.
              </small>
            </div>
          )}
          {assessment.microphoneRequired && (
            <div className="media-card">
              <div className="section-title">
                <AudioLines size={18} />
                <h4>Sound check</h4>
              </div>
              <label>
                Microphone input level
                <meter min={0} max={1} value={level} />
              </label>
              <p className="muted">
                Speak briefly to test your input, then check your speakers or
                headphones.
              </p>
              <button
                type="button"
                className="secondary"
                onClick={() => void playback()}
              >
                <Volume2 size={16} />
                Play test sound
              </button>
              <label className="consent-row">
                <input
                  type="checkbox"
                  checked={!!checks.playback}
                  onChange={(e) =>
                    publish({ ...checks, playback: e.target.checked })
                  }
                />
                I heard the test sound
              </label>
            </div>
          )}
          <button
            type="button"
            className="secondary stop-preview"
            onClick={() => {
              stop();
              setBusy(false);
              setMessage(
                "Preview stopped. Run checks again if devices change.",
              );
            }}
          >
            Stop local preview
          </button>
        </div>
      )}
      {message && (
        <div role="alert" className="readiness-alert">
          <AlertCircle size={20} />
          <p>{message}</p>
        </div>
      )}
    </section>
  );
}
