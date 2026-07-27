import type { Metadata, Viewport } from "next";
import "./globals.css";
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
      const applyTheme = () => {
        const theme = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
        document.documentElement.dataset.theme = theme;
      };
      applyTheme();
      window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", applyTheme);
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
          <div className="w-full max-w-md bg-white relative overflow-x-hidden" style={{ minHeight: "100dvh" }}>
            {children}
          </div>
        </TripLogProvider>
      </body>
    </html>
  );
}
