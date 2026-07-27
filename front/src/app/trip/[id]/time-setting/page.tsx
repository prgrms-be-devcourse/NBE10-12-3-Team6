"use client";

import { useEffect } from "react";
import { useRouter, useParams } from "next/navigation";

export default function TimeSettingRedirect() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();

  useEffect(() => {
    router.replace(`/trip/${id}`);
  }, [id, router]);

  return null;
}
