import { Panel } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ArrowRight, ClipboardCheck, FileText, GitBranch } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
export function Upcoming({ kind }: { kind: 'labels' | 'impact' | 'reviews' }) {
  const content = {
    labels: {
      title: 'Labels',
      subtitle: 'Connect every declaration to the correct formula version.',
      icon: FileText,
      steps: ['Link a formula version', 'Edit label declarations', 'Validate and submit'],
      stage: 'Sprint 2',
      link: '/formulas',
      cta: 'Browse formulas',
    },
    impact: {
      title: 'Change impact',
      subtitle: 'Find affected products and distinguish no-action results from required reviews.',
      icon: GitBranch,
      steps: ['Select a change', 'Analyze affected products', 'Create required reviews'],
      stage: 'Sprint 3',
      link: '/materials',
      cta: 'Browse materials',
    },
    reviews: {
      title: 'Review workspace',
      subtitle: 'Move labels through validation, independent review and controlled publication.',
      icon: ClipboardCheck,
      steps: ['Receive a review task', 'Review independently', 'Publish and retain history'],
      stage: 'Sprint 3',
      link: '/products',
      cta: 'Browse products',
    },
  }[kind]
  const navigate = useNavigate()
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">CONTROLLED WORKFLOW</div>
          <h1>{content.title}</h1>
          <p>{content.subtitle}</p>
        </div>
        <Badge variant="outline">{content.stage}</Badge>
      </div>
      <Panel className="upcoming">
        <span className="upcoming-icon">
          <content.icon size={35} />
        </span>
        <h2>This workflow is not connected yet</h2>
        <p>
          Task counts and results will appear when the workflow is connected. Explore products and formulas in the meantime.
        </p>
        <div className="workflow-steps">
          {content.steps.map((s, i) => (
            <div key={s}>
              <span>{i + 1}</span>
              <strong>{s}</strong>
            </div>
          ))}
        </div>
        <Button onClick={() => navigate(content.link)}>
          {content.cta}
          <ArrowRight size={16} />
        </Button>
      </Panel>
    </>
  )
}
