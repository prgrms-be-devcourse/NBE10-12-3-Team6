"use client";

import {
  CSSProperties,
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
  style?: CSSProperties;
};

export default function AnimatedBottomSheet({
  children,
  onClose,
  className = "",
  overlayClassName = "bg-black/40",
  zIndexClassName = "z-50",
  style,
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
        style={style}
      >
        <div className="sheet-collapse-control sticky top-0 z-20 flex h-8 shrink-0 items-center justify-center">
          <button
            type="button"
            onClick={close}
            aria-label="탭뷰 닫기"
            className="flex h-8 w-8 items-center justify-center rounded-full text-gray-500"
          >
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-gray-100">
              <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="m6 9 6 6 6-6" />
              </svg>
            </span>
          </button>
        </div>
        {children(close)}
      </div>
    </div>,
    portalTarget
  );
}
