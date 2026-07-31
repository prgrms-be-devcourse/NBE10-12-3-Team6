import type { Metadata, Viewport } from "next";
import "./globals.css";
import MobileKeyboardFocusGuard from "./components/MobileKeyboardFocusGuard";
import { TripLogProvider } from "./store";

export const metadata: Metadata = {
  title: "TripLog",
  description: "친구들과 여행을 계획하고, 순간을 기록하세요.",
  applicationName: "Triplog",
  appleWebApp: {
    capable: true,
    title: "Triplog",
    statusBarStyle: "default",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  interactiveWidget: "resizes-content",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f8f9fa" },
    { media: "(prefers-color-scheme: dark)", color: "#0f1014" },
  ],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const swScript = `if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js');`;

  const themeScript = `
    (() => {
      const themeStorageKey = "triplog-theme-preference";
      const colorSchemeMedia = window.matchMedia("(prefers-color-scheme: dark)");

      const getThemePreference = () => {
        try {
          const savedPreference = localStorage.getItem(themeStorageKey);
          return savedPreference === "light" || savedPreference === "dark"
            ? savedPreference
            : "system";
        } catch {
          return "system";
        }
      };

      const applyTheme = () => {
        const preference = getThemePreference();
        const theme = preference === "system"
          ? colorSchemeMedia.matches ? "dark" : "light"
          : preference;
        document.documentElement.dataset.theme = theme;
        document.documentElement.dataset.themePreference = preference;
      };

      applyTheme();
      colorSchemeMedia.addEventListener("change", applyTheme);
      window.addEventListener("triplog-theme-change", applyTheme);
    })();
  `;

  return (
    <html lang="ko" className="h-full" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: swScript }} />
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body className="min-h-full bg-gray-50 flex justify-center">
        <TripLogProvider>
          <MobileKeyboardFocusGuard />
          <div className="w-full max-w-md bg-white relative overflow-x-hidden" style={{ minHeight: "100dvh" }}>
            {children}
          </div>
        </TripLogProvider>
      </body>
    </html>
  );
}
