import { MutationTree } from 'vuex';
import { StateInterface, Pod } from './state';
import moment from 'moment';

export interface Payload {
  [key: string]: Pod | string | boolean | number | undefined;
}

const mutation: MutationTree<StateInterface> = {
  UPDATE_OR_INSERT_POD_ID (state: StateInterface, payload: { id: string, requestCount: number }): void {
    const podIndex = state.pods.findIndex(p => p.id == payload.id);
    if (podIndex >= 0) {
      state.pods[podIndex].id = payload.id;
      state.pods[podIndex].requestCount = payload.requestCount;
      state.pods[podIndex].lastUpdate = new Date();
    } else {
      console.log(`Adding new pod: ${payload.id}`);
      state.pods.push({id: payload.id, lastUpdate: new Date(), requestCount: payload.requestCount});
    }
    // .filter((p: { lastUpdate: moment.MomentInput; }) => moment().subtract(1, 'minute').isBefore(p.lastUpdate))
    const filteredPods = state.pods.filter(p => moment().subtract(10, 'second').isBefore(p.lastUpdate));
    state.pods = filteredPods;
  },
  UPDATE_CURRENT_POD (state: StateInterface, payload: {id: string, count: number}): void {
    state.currentPod = payload.id;
    state.sessionIncrement = payload.count;
  }
};

export default mutation;
