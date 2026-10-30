// @vitest-environment jsdom
import { it, expect, vi, afterEach } from "vitest";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from "@testing-library/react";
import { Readiness } from "./Readiness";
import { Content } from "./api";
vi.mock("./api", () => ({ api: vi.fn().mockResolvedValue({}) }));
const assessment = {
  cameraRequired: true,
  microphoneRequired: false,
} as Content;
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
it("stops a stream granted after the component was closed", async () => {
  let grant!: (s: unknown) => void;
  const stop = vi.fn();
  const getUserMedia = vi.fn(
    () =>
      new Promise((resolve) => {
        grant = resolve;
      }),
  );
  Object.defineProperty(navigator, "mediaDevices", {
    configurable: true,
    value: { getUserMedia },
  });
  const view = render(<Readiness assessment={assessment} onChange={vi.fn()} />);
  fireEvent.click(screen.getByText("Run system checks"));
  await waitFor(() => expect(getUserMedia).toHaveBeenCalled());
  view.unmount();
  grant({ getTracks: () => [{ stop }] });
  await waitFor(() => expect(stop).toHaveBeenCalled());
});
it("reports denied permission and leaves mandatory camera failed", async () => {
  Object.defineProperty(navigator, "mediaDevices", {
    configurable: true,
    value: { getUserMedia: vi.fn().mockRejectedValue(new Error("Denied")) },
  });
  const changed = vi.fn();
  render(<Readiness assessment={assessment} onChange={changed} />);
  fireEvent.click(screen.getByText("Run system checks"));
  await waitFor(() =>
    expect(screen.getByText(/Allow device permission/)).toBeTruthy(),
  );
  expect(changed.mock.lastCall?.[0].camera).toBe(false);
  expect(
    screen.getByRole("article", { name: "Webcam: Failed" }),
  ).toBeTruthy();
  expect(
    screen.getByRole("button", { name: "Retry Webcam" }),
  ).toBeTruthy();
  expect(
    screen.getByRole("article", {
      name: "Microphone: Not required",
    }),
  ).toBeTruthy();
});
it("stops local preview tracks on unmount without uploading media", async () => {
  const stop = vi.fn(),
    stream = { getTracks: () => [{ stop, addEventListener: vi.fn() }] };
  Object.defineProperty(navigator, "mediaDevices", {
    configurable: true,
    value: { getUserMedia: vi.fn().mockResolvedValue(stream) },
  });
  vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue();
  const changed = vi.fn();
  const view = render(<Readiness assessment={assessment} onChange={changed} />);
  fireEvent.click(screen.getByText("Run system checks"));
  await waitFor(() => expect(changed.mock.lastCall?.[0].camera).toBe(true));
  view.unmount();
  expect(stop).toHaveBeenCalled();
});
it("ignores ended callbacks from a previous check and invalidates changed devices", async () => {
  const ended: (() => void)[] = [];
  let changedDevice!: () => void;
  Object.defineProperty(navigator, "mediaDevices", {
    configurable: true,
    value: {
      getUserMedia: vi.fn(async () => ({
        getTracks: () => [
          {
            stop: vi.fn(),
            addEventListener: (_name: string, handler: () => void) =>
              ended.push(handler),
          },
        ],
      })),
      addEventListener: (_name: string, handler: () => void) => {
        changedDevice = handler;
      },
      removeEventListener: vi.fn(),
    },
  });
  vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue();
  const changed = vi.fn();
  render(<Readiness assessment={assessment} onChange={changed} />);
  fireEvent.click(screen.getByText("Run system checks"));
  await waitFor(() => expect(changed.mock.lastCall?.[0].camera).toBe(true));
  fireEvent.click(screen.getByText("Run system checks"));
  await waitFor(() => expect(ended).toHaveLength(2));
  ended[0]();
  expect(changed.mock.lastCall?.[0].camera).toBe(true);
  changedDevice();
  await waitFor(() => expect(changed.mock.lastCall?.[0].camera).toBe(false));
});
