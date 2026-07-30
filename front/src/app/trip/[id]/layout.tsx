import type { ReactNode } from "react";
import { TripEventProvider } from "./TripEventProvider";

export default function TripLayout({ children }: { children: ReactNode }) {
  return (
    <TripEventProvider>
      {children}
    </TripEventProvider>
  );
}
