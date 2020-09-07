<template>
  <q-page class="row items-center justify-evenly col">
    <q-card
      v-for="pod in pods"
      :key="pod.id"
      class="col-2"
      style="width: 24rem; height: 12rem; margin: 0.3rem 0.3rem 0.3rem 0.3rem;"
      :class="cardClass(pod.id)">
      <q-card-section>
        <q-toolbar-title style="width: 100%; text-align: center;">Pod</q-toolbar-title>
      </q-card-section>
      <q-card-section style="text-align: center;">{{ pod.id }}</q-card-section>
      <q-card-section style="text-align: center; font-size: 2rem;">{{ pod.lastUpdate | formatDateTime }}</q-card-section>
    </q-card>
  </q-page>
</template>

<script lang="ts">
import { Vue, Component } from 'vue-property-decorator';
import { Pod } from '../store/j4k/state';
import EventBus from 'vertx3-eventbus-client';
import { CustomWindow } from 'src/@types/CustomWindow';
import moment from 'moment';

@Component({
  filters: {
    formatDateTime(timestamp: Date) {
      return moment(timestamp).format('YYYY-MM-DD H:mm:ss.SSS');
    }
  }
})
export default class PageIndex extends Vue {
  [x: string]: any;
  eb: EventBus.EventBus | undefined;
  pingIntervalHandle?: number;

  window: CustomWindow = window;

  readonly options = {
    vertxbus_reconnect_attempts_max: Infinity, // Max reconnect attempts
    vertxbus_reconnect_delay_min: 1000, // Initial delay (in ms) before first reconnect attempt
    vertxbus_reconnect_delay_max: 5000, // Max delay (in ms) between reconnect attempts
    vertxbus_reconnect_exponent: 2, // Exponential backoff factor
    vertxbus_randomization_factor: 0.5 // Randomization factor between 0 and 1
  };

  /**
   * Computed property getter for the pod list
   */
  get pods(): Pod[] {
    return this.$store.state.j4k.pods.filter((p: { lastUpdate: moment.MomentInput; }) => moment().subtract(1, 'minute').isBefore(p.lastUpdate));
  }

  /**
   * If podId matches the currently connected pod, return a CSS class to be
   * applied to the card for that pod.
   */
  cardClass(podId: string): string | undefined {
    if (podId == this.$store.state.j4k.currentPod) {
      return "highlightedCard";
    }
    return;
  }

  startListening(): void {
    this.eb!.registerHandler('status', (err: any, msg: { body: { id: string; }; }) => {
      if (err) {
        console.log(`Error: ${JSON.stringify(err)}`);
      } else {
        this.$store.commit('j4k/SET_FROM_KEYS', msg.body.id);
      }
    });
  }

  mounted() {
    this.eb = new EventBus(`${this.window.apiAddr}/eventbus`, this.options);
    this.eb.onopen = this.startListening;
    this.eb.onreconnect = this.startListening;
  }
};
</script>

<style lang="sass" scoped>
.highlightedCard
  background-color: $secondary
  color: $dark
  font-weight: bold
</style>
