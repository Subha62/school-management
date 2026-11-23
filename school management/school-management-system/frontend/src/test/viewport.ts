/** jsdom has no layout engine; emulate matchMedia for min/max-width queries so MUI breakpoints work. */
export function setViewportWidth(width: number): void {
  window.matchMedia = (query: string): MediaQueryList => {
    const min = /min-width:\s*(\d+(?:\.\d+)?)px/.exec(query);
    const max = /max-width:\s*(\d+(?:\.\d+)?)px/.exec(query);
    const matches =
      (min?.[1] === undefined || width >= Number(min[1])) &&
      (max?.[1] === undefined || width <= Number(max[1]));
    return {
      matches,
      media: query,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false,
    };
  };
}
