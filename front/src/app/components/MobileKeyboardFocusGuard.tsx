"use client";

import { useEffect } from "react";

const KEYBOARD_EDGE_GAP_PX = 24;
const KEYBOARD_OPEN_DELAY_MS = 250;
const KEYBOARD_CLOSE_DELAY_MS = 450;
const KEYBOARD_HEIGHT_THRESHOLD_PX = 80;

const isTextEntryElement = (element: Element | null): element is HTMLElement => {
  if (element instanceof HTMLTextAreaElement) return true;
  if (element instanceof HTMLElement && element.isContentEditable) return true;
  if (!(element instanceof HTMLInputElement)) return false;

  return ![
    "button",
    "checkbox",
    "color",
    "file",
    "hidden",
    "image",
    "radio",
    "range",
    "reset",
    "submit",
  ].includes(element.type);
};

type ScrollPosition = {
  element: HTMLElement;
  left: number;
  top: number;
};

type KeyboardSession = {
  adjustedByGuard: boolean;
  keyboardWasOpen: boolean;
  scrollPositions: ScrollPosition[];
  windowX: number;
  windowY: number;
};

const captureScrollableAncestors = (element: HTMLElement): ScrollPosition[] => {
  const positions: ScrollPosition[] = [];
  let parent = element.parentElement;

  while (parent && parent !== document.body) {
    const style = window.getComputedStyle(parent);
    const scrollableY =
      parent.scrollHeight > parent.clientHeight &&
      /(auto|scroll|hidden)/.test(style.overflowY);
    const scrollableX =
      parent.scrollWidth > parent.clientWidth &&
      /(auto|scroll|hidden)/.test(style.overflowX);

    if (scrollableY || scrollableX) {
      positions.push({
        element: parent,
        left: parent.scrollLeft,
        top: parent.scrollTop,
      });
    }
    parent = parent.parentElement;
  }

  return positions;
};

export default function MobileKeyboardFocusGuard() {
  useEffect(() => {
    const usesVirtualKeyboard =
      window.matchMedia("(pointer: coarse)").matches ||
      navigator.maxTouchPoints > 0;
    if (!usesVirtualKeyboard) return;

    const viewport = window.visualViewport;
    let baselineViewportHeight = viewport?.height ?? window.innerHeight;
    let focusTimer: number | null = null;
    let restoreTimer: number | null = null;
    let keyboardSession: KeyboardSession | null = null;

    const currentViewportHeight = () => viewport?.height ?? window.innerHeight;

    const beginKeyboardSession = (element: HTMLElement) => {
      if (keyboardSession) return;
      keyboardSession = {
        adjustedByGuard: false,
        keyboardWasOpen: false,
        scrollPositions: captureScrollableAncestors(element),
        windowX: window.scrollX,
        windowY: window.scrollY,
      };
    };

    const restoreKeyboardSession = () => {
      if (!keyboardSession) return;

      const session = keyboardSession;
      keyboardSession = null;
      if (!session.adjustedByGuard && !session.keyboardWasOpen) return;

      [...session.scrollPositions].reverse().forEach(position => {
        position.element.scrollTo({
          left: position.left,
          top: position.top,
          behavior: "auto",
        });
      });
      window.scrollTo({
        left: session.windowX,
        top: session.windowY,
        behavior: "auto",
      });
    };

    const keepFocusedFieldVisible = () => {
      const focusedElement = document.activeElement;
      if (!isTextEntryElement(focusedElement)) return;
      beginKeyboardSession(focusedElement);

      const viewportTop = viewport?.offsetTop ?? 0;
      const viewportHeight = currentViewportHeight();
      const visibleTop = viewportTop + KEYBOARD_EDGE_GAP_PX;
      const visibleBottom = viewportTop + viewportHeight - KEYBOARD_EDGE_GAP_PX;
      const fieldRect = focusedElement.getBoundingClientRect();

      if (fieldRect.top >= visibleTop && fieldRect.bottom <= visibleBottom) return;

      if (keyboardSession) keyboardSession.adjustedByGuard = true;
      focusedElement.scrollIntoView({
        behavior: "smooth",
        block: "center",
        inline: "nearest",
      });
    };

    const scheduleVisibilityCheck = (delay = 50) => {
      if (focusTimer) window.clearTimeout(focusTimer);
      focusTimer = window.setTimeout(keepFocusedFieldVisible, delay);
    };

    const handleFocusIn = (event: FocusEvent) => {
      const target = event.target as Element | null;
      if (!isTextEntryElement(target)) return;

      if (restoreTimer) {
        window.clearTimeout(restoreTimer);
        restoreTimer = null;
      }
      baselineViewportHeight = Math.max(
        baselineViewportHeight,
        currentViewportHeight(),
      );
      beginKeyboardSession(target);
      scheduleVisibilityCheck(KEYBOARD_OPEN_DELAY_MS);
    };

    const handleFocusOut = () => {
      if (restoreTimer) window.clearTimeout(restoreTimer);
      restoreTimer = window.setTimeout(() => {
        if (isTextEntryElement(document.activeElement)) return;
        restoreKeyboardSession();
        baselineViewportHeight = currentViewportHeight();
      }, KEYBOARD_CLOSE_DELAY_MS);
    };

    const handleViewportChange = () => {
      const focusedElement = document.activeElement;
      const viewportHeight = currentViewportHeight();

      if (!isTextEntryElement(focusedElement)) {
        if (keyboardSession) restoreKeyboardSession();
        baselineViewportHeight = viewportHeight;
        return;
      }

      beginKeyboardSession(focusedElement);
      if (
        keyboardSession &&
        viewportHeight <
          baselineViewportHeight - KEYBOARD_HEIGHT_THRESHOLD_PX
      ) {
        keyboardSession.keyboardWasOpen = true;
      } else if (
        keyboardSession?.keyboardWasOpen &&
        viewportHeight >=
          baselineViewportHeight - KEYBOARD_EDGE_GAP_PX
      ) {
        restoreKeyboardSession();
        baselineViewportHeight = viewportHeight;
        return;
      }

      scheduleVisibilityCheck();
    };

    document.addEventListener("focusin", handleFocusIn);
    document.addEventListener("focusout", handleFocusOut);
    window.addEventListener("resize", handleViewportChange);
    viewport?.addEventListener("resize", handleViewportChange);
    viewport?.addEventListener("scroll", handleViewportChange);

    return () => {
      if (focusTimer) window.clearTimeout(focusTimer);
      if (restoreTimer) window.clearTimeout(restoreTimer);
      document.removeEventListener("focusin", handleFocusIn);
      document.removeEventListener("focusout", handleFocusOut);
      window.removeEventListener("resize", handleViewportChange);
      viewport?.removeEventListener("resize", handleViewportChange);
      viewport?.removeEventListener("scroll", handleViewportChange);
    };
  }, []);

  return null;
}
