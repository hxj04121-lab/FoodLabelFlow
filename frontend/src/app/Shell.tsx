import { Panel } from '@/components/catalog-shared'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Catalog } from '@/pages/Catalog'
import { HealthPage } from '@/pages/HealthPage'
import { Materials } from '@/pages/Materials'
import { Overview } from '@/pages/Overview'
import { Upcoming } from '@/pages/Upcoming'
import {
  Activity,
  Box,
  ChevronRight,
  CircleHelp,
  ClipboardCheck,
  FileText,
  FlaskConical,
  GitBranch,
  Layers3,
  LayoutDashboard,
  Menu,
  Package,
  Search,
  ShieldCheck,
  Truck,
  X,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import {
  NavLink,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom'
const navigation = [
  { path: '/', label: '工作概览', icon: LayoutDashboard },
  { path: '/suppliers', label: '供应商', icon: Truck },
  { path: '/materials', label: '物料与规格', icon: Layers3 },
  { path: '/products', label: '产品目录', icon: Package },
  { path: '/formulas', label: '配方版本', icon: FlaskConical },
  { path: '/labels', label: '标签管理', icon: FileText },
  { path: '/impact', label: '变更影响', icon: GitBranch },
  { path: '/reviews', label: '审核工作台', icon: ClipboardCheck },
]
export function Shell() {
  const [mobile, setMobile] = useState(false)
  const [help, setHelp] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()
  useEffect(() => {
    setMobile(false)
  }, [location.pathname])
  const active = navigation.find((n) => n.path === location.pathname)
  return (
    <div className="app-shell">
      <a href="#main" className="skip-link">
        跳到主要内容
      </a>
      <header className="topbar">
        <div className="wordmark">
          <span className="logo">
            <Layers3 size={23} />
          </span>
          SpecTrace<span className="workspace-label">食品标签工作空间</span>
        </div>
        <div className="topbar-right">
          <span className="environment">
            <span />
            本地工作空间
          </span>
          <button aria-label="使用帮助" onClick={() => setHelp(true)}>
            <CircleHelp size={19} />
          </button>
          <span className="topbar-divider" />
          <span className="avatar">XF</span>
        </div>
      </header>
      {mobile && (
        <button
          className="mobile-overlay"
          aria-label="关闭导航遮罩"
          onClick={() => setMobile(false)}
        />
      )}
      <aside className={`sidebar ${mobile ? 'is-open' : ''}`}>
        <div className="workspace-switch">
          <span className="workspace-icon">
            <Box size={21} />
          </span>
          <div>
            <strong>FoodLabelFlow</strong>
            <small>TEAM 16 · SWE5006</small>
          </div>
          <button
            className="mobile-close"
            aria-label="关闭导航"
            onClick={() => setMobile(false)}
          >
            <X size={18} />
          </button>
        </div>
        <p className="nav-section-label">工作空间</p>
        <nav aria-label="主导航">
          {navigation.map((n, i) => (
            <NavLink
              end={n.path === '/'}
              to={n.path}
              key={n.path}
              className={({ isActive }) =>
                `nav-item ${isActive ? 'active' : ''}`
              }
            >
              <n.icon size={19} />
              <span>{n.label}</span>
              {i > 4 && <span className="nav-dot" />}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="version-note">
            <span className="version-icon">
              <ShieldCheck size={21} />
            </span>
            <strong>版本始终可追溯</strong>
            <p>保留过去，准确管理每一次变化。</p>
          </div>
          <NavLink
            to="/health"
            className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
          >
            <Activity size={18} />
            <span>系统状态</span>
          </NavLink>
          <button className="nav-item" onClick={() => setHelp(true)}>
            <CircleHelp size={18} />
            <span>使用指南</span>
          </button>
          <div className="user-card">
            <span className="avatar">XF</span>
            <div>
              <strong>Xu Feiyang</strong>
              <small>M3 · 本地预览身份</small>
            </div>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <div className="contextbar">
          <div className="breadcrumb">
            <button
              className="mobile-toggle"
              aria-label="展开导航"
              onClick={() => setMobile(true)}
            >
              <Menu size={20} />
            </button>
            <span>工作空间</span>
            <ChevronRight size={14} />
            <strong>
              {active?.label ??
                (location.pathname === '/health' ? '系统状态' : '页面')}
            </strong>
          </div>
          <button
            className="quick-search"
            onClick={() => navigate('/products')}
          >
            <Search size={16} />
            <span>搜索产品与配方</span>
          </button>
        </div>
        <main id="main">
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/products" element={<Catalog />} />
            <Route path="/formulas" element={<Catalog formulas />} />
            <Route path="/suppliers" element={<Materials suppliers />} />
            <Route path="/materials" element={<Materials />} />
            <Route path="/labels" element={<Upcoming kind="labels" />} />
            <Route path="/impact" element={<Upcoming kind="impact" />} />
            <Route path="/reviews" element={<Upcoming kind="reviews" />} />
            <Route path="/health" element={<HealthPage />} />
            <Route
              path="*"
              element={
                <Panel className="upcoming">
                  <h1>页面不存在</h1>
                  <Button onClick={() => navigate('/')}>返回工作概览</Button>
                </Panel>
              }
            />
          </Routes>
          <footer className="page-footer">
            <span>SpecTrace · Food label change control</span>
            <span>本地预览 / 单市场 MVP</span>
          </footer>
        </main>
      </div>
      <Dialog open={help} onOpenChange={setHelp}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>欢迎使用 SpecTrace</DialogTitle>
            <DialogDescription>
              当前版本支持浏览基线产品、物料和配方追溯。
            </DialogDescription>
          </DialogHeader>
          <ol className="help-list">
            <li>在产品目录中搜索名称、品牌或编号，点击产品查看配方。</li>
            <li>在详情弹窗中切换配方、版本历史与数据来源。</li>
            <li>供应商和物料页面可查看具体规格和成分。</li>
            <li>业务数据为 V3 种子快照；只有系统状态页读取实时健康接口。</li>
            <li>本地预览身份不代表已登录，创建、发布和审批尚未开放。</li>
          </ol>
        </DialogContent>
      </Dialog>
    </div>
  )
}
