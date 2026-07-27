import type { MetadataRoute } from 'next'

export default function manifest(): MetadataRoute.Manifest {
  return {
    id: '/',
    name: 'Triplog',
    short_name: 'Triplog',
    description: '함께 만드는 여행 계획',
    lang: 'ko',
    start_url: '/',
    display: 'standalone',
    background_color: '#f8f9fa',
    theme_color: '#f8f9fa',
    icons: [
      { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
      { src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
      { src: '/icon-maskable.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  }
}
