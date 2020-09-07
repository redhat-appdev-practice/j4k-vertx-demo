import { MutationTree } from 'vuex';
import { StateInterface, Pod } from './state';
import { Vue } from 'vue-property-decorator';

export interface Payload {
  [key: string]: Pod | string | boolean | number | undefined;
}

const mutation: MutationTree<StateInterface> = {
  SET_FROM_KEYS (state: StateInterface, payload: string): void {
    const podIndex = state.pods.findIndex(p => p.id == payload);
    if (podIndex >= 0) {
      state.pods[podIndex].id = payload;
      state.pods[podIndex].lastUpdate = new Date();
    } else {
      console.log(`Adding new pod: ${payload}`);
      state.pods.push({id: payload, lastUpdate: new Date()});
    }
  },
  DELETE_KEY(state: StateInterface, payload: Payload): void {
    const keyList = Object.keys(payload);
    const filteredPods = state.pods.filter(p => keyList.indexOf(p.id) == -1);
    Vue.set(state, 'pods', filteredPods);
  }
};

export default mutation;
