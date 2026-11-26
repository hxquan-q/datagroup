import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

const mocks = vi.hoisted(() => ({
  clearQueryCache: vi.fn(),
  routerPush: vi.fn(),
  apiLogin: vi.fn(),
  apiGuestLogin: vi.fn(),
  apiRegister: vi.fn(),
  apiMe: vi.fn(),
}));

vi.mock("~/plugins/query", () => ({
  queryClient: { clear: mocks.clearQueryCache },
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: mocks.routerPush }),
}));

vi.mock("~/api/auth", () => ({
  login: mocks.apiLogin,
  guestLogin: mocks.apiGuestLogin,
  register: mocks.apiRegister,
  me: mocks.apiMe,
}));

import { useAuthStore } from "../auth";

describe("auth store query cache lifecycle", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
    vi.stubGlobal("localStorage", {
      getItem: vi.fn(() => null),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    });
  });

  it("clears cached server state before storing a new login token", async () => {
    mocks.apiLogin.mockResolvedValue({ token: "new-token" });
    mocks.apiMe.mockResolvedValue({ id: "u1", name: "alice" });
    const store = useAuthStore();

    await store.login("alice", "password");

    expect(mocks.clearQueryCache).toHaveBeenCalledTimes(1);
    expect(localStorage.setItem).toHaveBeenCalledWith("access_token", "new-token");
  });

  it("clears cached server state on logout", () => {
    const store = useAuthStore();

    store.logout();

    expect(mocks.clearQueryCache).toHaveBeenCalledTimes(1);
    expect(localStorage.removeItem).toHaveBeenCalledWith("access_token");
    expect(mocks.routerPush).toHaveBeenCalledWith("/login");
  });
});
