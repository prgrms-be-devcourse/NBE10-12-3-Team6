import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  reactStrictMode: false,
  allowedDevOrigins: ["192.168.0.5", '172.30.1.87'],
};

export default nextConfig;
