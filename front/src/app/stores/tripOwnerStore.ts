import { create } from "zustand";

type TripOwnerState = {
  ownerId: number | null;
};

type TripOwnerActions = {
  setOwnerId: (id: number) => void;
  clearOwnerId: () => void;
};

export const useTripOwnerStore = create<TripOwnerState & TripOwnerActions>((set) => ({
  ownerId: null,
  setOwnerId: (id) => set({ ownerId: id }),
  clearOwnerId: () => set({ ownerId: null }),
}));
