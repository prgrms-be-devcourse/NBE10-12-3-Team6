"use client";

import {
  ReactNode,
  useCallback,
  useEffect,
  useState,
} from "react";
import { createPortal } from "react-dom";

const SHEET_EXIT_MS = 220;

type VisibleViewport = {
  height: number;
  offsetTop: number;
};

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
  const [visibleViewport, setVisibleViewport] = useState<VisibleViewport | null>(null);

  useEffect(() => {
    setPortalTarget(document.body);

    const viewport = window.visualViewport;
    const updateVisibleViewport = () => {
      const nextViewport = {
        height: Math.round(viewport?.height ?? window.innerHeight),
        offsetTop: Math.round(viewport?.offsetTop ?? 0),
      };
      setVisibleViewport(current => (
        current?.height === nextViewport.height &&
        current.offsetTop === nextViewport.offsetTop
          ? current
          : nextViewport
      ));
    };

    updateVisibleViewport();
    window.addEventListener("resize", updateVisibleViewport);
    viewport?.addEventListener("resize", updateVisibleViewport);
    viewport?.addEventListener("scroll", updateVisibleViewport);

    return () => {
      window.removeEventListener("resize", updateVisibleViewport);
      viewport?.removeEventListener("resize", updateVisibleViewport);
      viewport?.removeEventListener("scroll", updateVisibleViewport);
    };
  }, []);

  const close = useCallback(() => {
    if (closing) return;
    setClosing(true);
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.setTimeout(onClose, reduceMotion ? 0 : SHEET_EXIT_MS);
  }, [closing, onClose]);

  if (!portalTarget) return null;

  return createPortal(
    <div
      className={`fixed inset-0 ${zIndexClassName} flex items-end justify-center`}
      style={visibleViewport ? {
        height: `${visibleViewport.height}px`,
        top: `${visibleViewport.offsetTop}px`,
        bottom: "auto",
      } : undefined}
    >
      <div
        className={`sheet-backdrop absolute inset-0 ${overlayClassName} ${closing ? "is-closing" : ""}`}
        onClick={close}
      />
      <div
        className={`sheet-panel relative w-full max-w-md max-h-[calc(100%_-_0.75rem)] rounded-t-3xl ${closing ? "is-closing" : ""} ${className}`}
      >
        {children(close)}
      </div>
    </div>,
    portalTarget
  );
}
