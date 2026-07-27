"use client";

import { ReactNode, useCallback, useEffect, useState } from "react";
import { createPortal } from "react-dom";

const SHEET_EXIT_MS = 220;

type AnimatedBottomSheetProps = {
  children: (close: () => void) => ReactNode;
  onClose: () => void;
  className?: string;
  overlayClassName?: string;
  zIndexClassName?: string;
};

export default function AnimatedBottomSheet({
  children,
  onClose,
  className = "",
  overlayClassName = "bg-black/40",
  zIndexClassName = "z-50",
}: AnimatedBottomSheetProps) {
  const [closing, setClosing] = useState(false);
  const [portalTarget, setPortalTarget] = useState<HTMLElement | null>(null);

  useEffect(() => {
    setPortalTarget(document.body);
  }, []);

  const close = useCallback(() => {
    if (closing) return;
    setClosing(true);
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.setTimeout(onClose, reduceMotion ? 0 : SHEET_EXIT_MS);
  }, [closing, onClose]);

  if (!portalTarget) return null;

  return createPortal(
    <div className={`fixed inset-0 ${zIndexClassName} flex items-end justify-center`}>
      <div
        className={`sheet-backdrop absolute inset-0 ${overlayClassName} ${closing ? "is-closing" : ""}`}
        onClick={close}
      />
      <div className={`sheet-panel relative w-full max-w-md max-h-[calc(100dvh-0.75rem)] rounded-t-3xl ${closing ? "is-closing" : ""} ${className}`}>
        {children(close)}
      </div>
    </div>,
    portalTarget
  );
}
