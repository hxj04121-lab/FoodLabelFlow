import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const sprint = process.env.TEST_ARTIFACT_SPRINT || 'sprint2'
const artifactRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../test-artifacts', sprint)

export function testArtifactPath(...segments: string[]) {
  return resolve(artifactRoot, ...segments)
}
