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
  { path: '/', label: 'Overview', icon: LayoutDashboard },
  { path: '/suppliers', label: 'Suppliers', icon: Truck },
  { path: '/materials', label: 'Materials & specs', icon: Layers3 },
  { path: '/products', label: 'Products', icon: Package },
  { path: '/formulas', label: 'Formula versions', icon: FlaskConical },
  { path: '/labels', label: 'Labels', icon: FileText },
  { path: '/impact', label: 'Change impact', icon: GitBranch },
  { path: '/reviews', label: 'Review workspace', icon: ClipboardCheck },
]
import { CatalogConnection } from '@/components/CatalogConnection'
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
        Skip to main content
      </a>
      <header className="topbar">
        <div className="wordmark">
          <span className="logo">
            <Layers3 size={23} />
          </span>
          SpecTrace<span className="workspace-label">Food label workspace</span>
        </div>
        <div className="topbar-right">
          <span className="environment">
            <span />
            Local workspace
          </span>
          <button aria-label="Help" onClick={() => setHelp(true)}>
            <CircleHelp size={19} />
          </button>
          <span className="topbar-divider" />
          <span className="avatar">XF</span>
        </div>
      </header>
      {mobile && (
        <button
          className="mobile-overlay"
          aria-label="Dismiss navigation overlay"
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
            aria-label="Close navigation"
            onClick={() => setMobile(false)}
          >
            <X size={18} />
          </button>
        </div>
        <p className="nav-section-label">Workspace</p>
        <nav aria-label="Main navigation">
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
            <strong>Always traceable</strong>
            <p>Preserve history. Keep every change in view.</p>
          </div>
          <NavLink
            to="/health"
            className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
          >
            <Activity size={18} />
            <span>System status</span>
          </NavLink>
          <button className="nav-item" onClick={() => setHelp(true)}>
            <CircleHelp size={18} />
            <span>User guide</span>
          </button>
          <div className="user-card">
            <span className="avatar">XF</span>
            <div>
              <strong>Xu Feiyang</strong>
              <small>M3 · Preview profile</small>
            </div>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <div className="contextbar">
          <div className="breadcrumb">
            <button
              className="mobile-toggle"
              aria-label="Open navigation"
              onClick={() => setMobile(true)}
            >
              <Menu size={20} />
            </button>
            <span>Workspace</span>
            <ChevronRight size={14} />
            <strong>
              {active?.label ??
                (location.pathname === '/health' ? 'System status' : 'Page')}
            </strong>
          </div>
          <button
            className="quick-search"
            onClick={() => navigate('/products')}
          >
            <Search size={16} />
            <span>Find products and formulas</span>
          </button>
        </div>
        <main id="main">
          <Routes>
            <Route path="/" element={<CatalogConnection><Overview /></CatalogConnection>} />
            <Route path="/products" element={<CatalogConnection><Catalog /></CatalogConnection>} />
            <Route path="/formulas" element={<CatalogConnection><Catalog formulas /></CatalogConnection>} />
            <Route path="/suppliers" element={<CatalogConnection><Materials suppliers /></CatalogConnection>} />
            <Route path="/materials" element={<CatalogConnection><Materials /></CatalogConnection>} />
            <Route path="/labels" element={<Upcoming kind="labels" />} />
            <Route path="/impact" element={<Upcoming kind="impact" />} />
            <Route path="/reviews" element={<Upcoming kind="reviews" />} />
            <Route path="/health" element={<HealthPage />} />
            <Route
              path="*"
              element={
                <Panel className="upcoming">
                  <h1>Page not found</h1>
                  <Button onClick={() => navigate('/')}>Back to overview</Button>
                </Panel>
              }
            />
          </Routes>
          <footer className="page-footer">
            <span>SpecTrace · Food label change control</span>
            <span>Local workspace / Single-market MVP</span>
          </footer>
        </main>
      </div>
      <Dialog open={help} onOpenChange={setHelp}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Welcome to SpecTrace</DialogTitle>
            <DialogDescription>
              Browse products, materials and formula traceability.
            </DialogDescription>
          </DialogHeader>
          <ol className="help-list">
            <li>Search by product name, brand or ID, then select a product to view its formula.</li>
            <li>Use the detail tabs to view formula items, version history and sources.</li>
            <li>Browse suppliers and materials to inspect specifications and components.</li>
            <li>Catalog pages read from the M1 API. Refresh to reload; failed requests never fall back to offline data.</li>
            <li>The preview profile is not a signed-in account. Saving, publishing and approvals are not available.</li>
          </ol>
        </DialogContent>
      </Dialog>
    </div>
  )
}
