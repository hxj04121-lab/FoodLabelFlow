import { Panel } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ArrowRight, ClipboardCheck, FileText, GitBranch } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
export function Upcoming({ kind }: { kind: 'labels' | 'impact' | 'reviews' }) {
  const content = {
    labels: {
      title: '标签管理',
      subtitle: '让每一份声明，都对应准确的配方版本。',
      icon: FileText,
      steps: ['关联配方版本', '编辑标签声明', '校验与提交'],
      stage: 'Sprint 2',
      link: '/formulas',
      cta: '查看配方版本',
    },
    impact: {
      title: '变更影响',
      subtitle: '识别原料变化影响的产品，区分无需操作和待复核。',
      icon: GitBranch,
      steps: ['选择变更来源', '分析受影响产品', '创建必要的审查任务'],
      stage: 'Sprint 3',
      link: '/materials',
      cta: '查看物料与规格',
    },
    reviews: {
      title: '审核工作台',
      subtitle: '让标签经过校验、独立审核和受控发布。',
      icon: ClipboardCheck,
      steps: ['接收审查任务', '独立审核与批准', '发布并保留旧版本'],
      stage: 'Sprint 3',
      link: '/products',
      cta: '查看产品档案',
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
        <h2>工作区已规划，业务接口尚未接入</h2>
        <p>
          这里暂不显示任务数量或处理结果。你可以先浏览已经提供的产品与配方基线。
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
