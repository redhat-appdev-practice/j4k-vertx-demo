<template>
  <q-layout view="lHh Lpr lFf">
    <q-header elevated>
      <q-toolbar>
        <img alt="Red Hat" src="/static/logo.svg" class="logo" />
        <q-toolbar-title>
          J4K Demo
        </q-toolbar-title>
      </q-toolbar>
    </q-header>

    <q-page-container>
      <router-view />
    </q-page-container>
  </q-layout>
</template>

<script lang="ts">
import { Vue, Component } from 'vue-property-decorator';

@Component
export default class MainLayout extends Vue {
  leftDrawerOpen = false;
}
</script>

<style lang="sass" scoped>
.logo
  height: 4em
</style>
