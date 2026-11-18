export async function prepareQuizSnapshot() {
  const { initialState } = await import('../../js/store.js');
  const config = await (await fetch('/data/test_config.json')).json();
  localStorage.setItem('voplo.v1', JSON.stringify({
    ...initialState(config.version), testStarted:true, index:1
  }));
  location.hash = '#test';
}
