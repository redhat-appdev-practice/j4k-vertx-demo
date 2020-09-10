<template>
  <q-page class="row items-center justify-evenly col">
    <q-card
      v-for="pod in pods"
      :key="pod.id"
      class="col-2"
      style="width: 24rem; height: 12rem; margin: 0.3rem 0.3rem 0.3rem 0.3rem;"
      :class="cardClass(pod.id)">
      <q-card-section>
        <q-toolbar-title style="width: 100%; text-align: center;">Pod ({{ pod.requestCount }})</q-toolbar-title>
      </q-card-section>
      <q-card-section style="text-align: center;">{{ pod.id }}</q-card-section>
      <q-card-section style="text-align: center; font-size: 1.7rem;">{{ pod.lastUpdate | formatDateTime }}</q-card-section>
    </q-card>
  </q-page>
</template>

<script lang="ts">
import { Vue, Component } from 'vue-property-decorator';
import { Pod } from '../store/j4k/state';
import EventBus from 'vertx3-eventbus-client';
import moment from 'moment';
import Axios from 'axios';

@Component({
  filters: {
    formatDateTime(timestamp: Date) {
      return moment(timestamp).format('YYYY-MM-DD H:mm:ss.SSS');
    }
  }
})
export default class PageIndex extends Vue {
  eb: EventBus.EventBus | undefined;
  pingIntervalHandle?: number;
  reconnectIntervalHandle?: number;

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
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
    return this.$store.state.j4k.pods as Pod[];
  }

  /**
   * If podId matches the currently connected pod, return a CSS class to be
   * applied to the card for that pod.
   */
  cardClass(podId: string): string | undefined {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
    if (podId == this.$store.state.j4k.currentPod) {
      return 'highlightedCard';
    }
    return;
  }

  startListening(): void {
    // eslint-disable-next-line @typescript-eslint/no-non-null-assertion,@typescript-eslint/unbound-method
    this.eb!.onclose = this.connectEventBus;
    this.eb?.registerHandler('status', (err: never, msg: { body: { id: string; }; }) => {
      if (err) {
        this.$q.loading.hide();
        console.log(`Error: ${JSON.stringify(err)}`);
      } else {
        this.$store.commit('j4k/UPDATE_OR_INSERT_POD_ID', msg.body);
        this.$q.loading.hide();
      }
    });
  }

  connectEventBus(): void {
    // eslint-disable-next-line @typescript-eslint/restrict-template-expressions
    this.eb = new EventBus(`${window.location.origin}/eventbus`, this.options);
    // eslint-disable-next-line @typescript-eslint/unbound-method
    this.eb.onopen = this.startListening;
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  connectToNewPod(t: number): void {
      this.$q.loading.show();
      // Every X Seconds, disconnect and reconnect in order to access different hosts
      this.eb?.close();
      this.connectEventBus();
      // eslint-disable-next-line @typescript-eslint/restrict-template-expressions
      Axios.get(`${window.location.origin}/podinfo`)
        .then(value => {
          // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-assignment
          this.$store.commit('j4k/UPDATE_CURRENT_POD', {id: value.data.id, count: value.data.requestCount});
        }).catch(err => {
          console.log(`Error: ${JSON.stringify(err)}`);
          this.$q.loading.hide();
        });
  }

  mounted() {
    this.connectEventBus();

    // eslint-disable-next-line @typescript-eslint/unbound-method
    this.reconnectIntervalHandle = window.setInterval(this.connectToNewPod, 10000);
  }

  beforeDestroy(): void {
    window.clearInterval(this.pingIntervalHandle);
    window.clearInterval(this.reconnectIntervalHandle);
    this.eb?.close();
  }
};
</script>

<style lang="sass" scoped>
.highlightedCard
  background-color: $secondary
  color: $dark
  font-weight: bold
</style>
