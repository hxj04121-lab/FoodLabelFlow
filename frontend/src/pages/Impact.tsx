import { Panel, SectionHead } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ArrowRight, GitBranch } from 'lucide-react'
import { Link } from 'react-router-dom'

// SCRUM-71 foundation: enable selection and analysis only after the S3 contract
// and real services are connected. No guessed endpoints or preview findings.
export function Impact() {
  return (
    <div className="impact-page">
      <div className="page-title">
        <div>
          <h1>Change impact</h1>
          <p>Trace a specification change to the products and labels that need attention.</p>
        </div>
        <Badge variant="outline">Not connected</Badge>
      </div>

      <div className="source-notice impact-notice" role="status">
        <GitBranch size={18} aria-hidden="true" />
        <div>
          <strong>Impact analysis is not available yet</strong>
          <p>You can browse material specifications now. Selecting a change and running an analysis will be available when this workflow is connected.</p>
        </div>
      </div>

      <div className="impact-layout">
        <Panel>
          <SectionHead title="Specification change" />
          <div className="impact-content">
            <label htmlFor="impact-change">Change request</label>
            <select id="impact-change" disabled aria-describedby="impact-change-help">
              <option>Change selection unavailable</option>
            </select>
            <p id="impact-change-help">A change request identifies the material and its before and after specification versions.</p>
            <dl className="impact-context">
              <div><dt>Supplier material</dt><dd>Not selected</dd></div>
              <div><dt>Before specification</dt><dd>Not selected</dd></div>
              <div><dt>After specification</dt><dd>Not selected</dd></div>
            </dl>
            <Button disabled aria-describedby="impact-change-help">Run impact analysis</Button>
            <Button variant="outline" asChild>
              <Link to="/materials">Browse materials &amp; specs<ArrowRight aria-hidden="true" /></Link>
            </Button>
          </div>
        </Panel>

        <Panel>
          <SectionHead title="Analysis results" />
          <div className="impact-results" role="region" aria-label="Analysis results">
            <GitBranch size={28} aria-hidden="true" />
            <h3>Results are unavailable</h3>
            <p>When connected, this area will show the selected run, affected products and each finding.</p>
            <p>No analysis results have been loaded. This does not mean that no products are affected.</p>
          </div>
        </Panel>
      </div>

      <section className="impact-guide" aria-labelledby="impact-outcomes">
        <h2 id="impact-outcomes">Understanding the outcomes</h2>
        <p>These describe possible findings, not results for a selected change.</p>
        <dl>
          <div>
            <dt>No action <code>NO_ACTION</code></dt>
            <dd>The published label already covers the derived allergens. No review task is needed for this finding.</dd>
          </div>
          <div>
            <dt>Review required <code>REVIEW_REQUIRED</code></dt>
            <dd>The published label is missing a required declaration. A review task tracks the replacement label through validation and review.</dd>
          </div>
        </dl>
        <p>Analyzing a change does not publish a label. Review and publication are separate steps.</p>
      </section>
    </div>
  )
}
