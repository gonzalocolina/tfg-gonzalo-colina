<script setup>
const currentLocation = globalThis.location
const publicPort = String(__FRONTEND_PUBLIC_PORT__)
const publicOrigin =
  import.meta.env.DEV && currentLocation?.port && currentLocation.port !== publicPort
    ? `${currentLocation.protocol}//${currentLocation.hostname}:${publicPort}`
    : (currentLocation?.origin ?? '')

const playerJoinUrl = currentLocation
  ? new URL('/player/join', publicOrigin || currentLocation.origin).toString()
  : '/player/join'

const organizerLoginUrl = currentLocation
  ? new URL('/organizer/login', publicOrigin || currentLocation.origin).toString()
  : '/organizer/login'
</script>

<template>
  <main class="portal-shell">
    <section class="hero">
      <span class="eyebrow">Plataforma multijugador</span>
      <h1>Marvel Champions</h1>
      <p class="hero-tagline">Epic Multiplayer</p>
      <p class="hero-copy">
        Inicia sesión como organizador o únete a
        una partida con el código compartido del evento.
      </p>
    </section>

    <section class="entry-grid">
      <a class="entry-card entry-card--player" :href="playerJoinUrl">
        <span class="entry-badge">Móvil</span>
        <h2>Unirse a la partida</h2>
        <p>Acceso rápido para jugadores desde el teléfono con el código del evento.</p>
        <span class="entry-link">Abrir acceso de jugador →</span>
      </a>

      <a class="entry-card entry-card--organizer" :href="organizerLoginUrl">
        <span class="entry-badge">Escritorio</span>

        <h2>Organizar una partida</h2>
        <p>Inicia sesión para crear eventos, configurarlos y controlar la partida en directo.</p>
        <span class="entry-link">Abrir acceso de organizador →</span>
      </a>
    </section>

    <p class="portal-hint">
      Los organizadores gestionan eventos desde escritorio · Los jugadores se unen con el código
      del evento
    </p>
  </main>
</template>
