"use client";

import { useEffect, useState, type ReactNode } from "react";

export default function Template({ children }: { children: ReactNode }) {
  const [skipAnimation, setSkipAnimation] = useState(false);

  useEffect(() => {
    const shouldSkip = sessionStorage.getItem("skip-next-route-animation") === "true";
    if (!shouldSkip) return;

    sessionStorage.removeItem("skip-next-route-animation");
    setSkipAnimation(true);
    window.setTimeout(() => {
      delete document.documentElement.dataset.skipRouteAnimation;
      setSkipAnimation(false);
    }, 120);
  }, []);

  return (
    <div className={`${skipAnimation ? "" : "app-route-transition"} min-h-screen`}>
      {children}
    </div>
  );
}
