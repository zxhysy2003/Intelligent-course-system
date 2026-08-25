export function createStudyEventId() {
  if (globalThis.crypto?.randomUUID) {
    return `study-${globalThis.crypto.randomUUID()}`
  }
  if (globalThis.crypto?.getRandomValues) {
    const values = new Uint32Array(4)
    globalThis.crypto.getRandomValues(values)
    const randomPart = Array.from(values, (value) => value.toString(16).padStart(8, '0')).join('-')
    return `study-${randomPart}`
  }
  return `study-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}
