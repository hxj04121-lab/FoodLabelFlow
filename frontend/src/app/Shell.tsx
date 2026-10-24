import { Panel } from '@/components/catalog-shared'
import { isLocalDemoHost } from '@/lib/local-demo'
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
import { Impact } from '@/pages/Impact'
import { LabelHistory } from '@/pages/LabelHistory'
import { Labels } from '@/pages/Labels'
import { Reviews } from '@/pages/Reviews'
import { Materials } from '@/pages/Materials'
import { Overview } from '@/pages/Overview'
import { Upcoming } from '@/pages/Upcoming'
import {
  Activity,
  ChevronRight,
  CircleHelp,
  ClipboardCheck,
  FileText,
  FlaskConical,
  GitBranch,
  History,
  Layers3,
  LayoutDashboard,
  Menu,
  Package,
  Search,
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
  {
    section: 'Catalog',
    items: [
      { path: '/', label: 'Overview', icon: LayoutDashboard },
      { path: '/suppliers', label: 'Suppliers', icon: Truck },
      { path: '/materials', label: 'Materials & specs', icon: Layers3 },
      { path: '/products', label: 'Products', icon: Package },
      { path: '/formulas', label: 'Formula versions', icon: FlaskConical },
    ],
  },
  {
    section: 'Change control',
    items: [
      { path: '/labels', label: 'Labels', icon: FileText },
      { path: '/impact', label: 'Change impact', icon: GitBranch },
      { path: '/reviews', label: 'Review workspace', icon: ClipboardCheck },
      { path: '/label-history', label: 'Label history', icon: History },
    ],
  },
]
import { CatalogConnection } from '@/components/CatalogConnection'
import { ControlledDemoIdentitySwitcher, useCurrentIdentity } from '@/components/CurrentIdentityPanel'
export function Shell() {
  const [mobile, setMobile] = useState(false)
  const [help, setHelp] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()
  const identity = useCurrentIdentity()
  const workspaceName = isLocalDemoHost() ? 'Course demo' : 'Connected workspace'
  useEffect(() => {
    setMobile(false)
  }, [location.pathname])
  const activeGroup = navigation.find((g) =>
    g.items.some((n) => n.path === location.pathname),
  )
  const active = activeGroup?.items.find((n) => n.path === location.pathname)
  const initials = identity.session.actor?.displayName?.slice(0, 2).toUpperCase() ?? '—'
  return (
    <div className="app-shell">
      <a href="#main" className="skip-link">
        Skip to main content
      </a>
      {mobile && (
        <button
          className="mobile-overlay"
          aria-label="Dismiss navigation overlay"
          onClick={() => setMobile(false)}
        />
      )}
      <aside className={`sidebar ${mobile ? 'is-open' : ''}`}>
        <div className="wordmark">
          <span className="logo">
            <Layers3 size={19} />
          </span>
          <div>
            <strong>SpecTrace</strong>
            <small>FoodLabelFlow · Team 16</small>
          </div>
          <button
            className="mobile-close"
            aria-label="Close navigation"
            onClick={() => setMobile(false)}
          >
            <X size={18} />
          </button>
        </div>
        <nav className="sidebar-nav" aria-label="Main navigation">
          {navigation.map((group) => (
            <div className="nav-group" key={group.section}>
              <p className="nav-section-label">{group.section}</p>
              {group.items.map((n) => (
                <NavLink
                  end={n.path === '/'}
                  to={n.path}
                  key={n.path}
                  className={({ isActive }) =>
                    `nav-item ${isActive ? 'active' : ''}`
                  }
                >
                  <n.icon size={18} />
                  <span>{n.label}</span>
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="environment-card">
            <strong>{workspaceName}</strong>
            <span>Single-market MVP · United States</span>
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
            <span className="avatar">{initials}</span>
            <div>
              <strong>{identity.session.actor?.displayName ?? 'Identity unavailable'}</strong>
              <small>{identity.session.actor?.roles.join(', ') ?? 'No verified role'}</small>
            </div>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="mobile-toggle"
              aria-label="Open navigation"
              onClick={() => setMobile(true)}
            >
              <Menu size={20} />
            </button>
            <span className="breadcrumb-section">
              {activeGroup?.section ?? 'Workspace'}
            </span>
            <ChevronRight className="breadcrumb-section" size={14} aria-hidden="true" />
            <strong>
              {active?.label ??
                (location.pathname === '/health' ? 'System status' : 'Page')}
            </strong>
          </div>
          <div className="topbar-right">
            <button
              className="quick-search"
              onClick={() => navigate('/products')}
            >
              <Search size={16} />
              <span>Find products and formulas</span>
            </button>
            <button className="icon-button" aria-label="Help" onClick={() => setHelp(true)}>
              <CircleHelp size={18} />
            </button>
            <span className="topbar-divider" />
            <ControlledDemoIdentitySwitcher showStatus={false} />
            <span className="identity-status" aria-live="polite">
              {identity.session.status === 'switching' ? 'Refreshing permissions…'
                : identity.session.actor?.displayName || identity.session.error || 'Identity unavailable'}
            </span>
            <span className="avatar" title={identity.session.actor?.roles.join(', ')}>{initials}</span>
          </div>
        </header>
        <main id="main">
          <Routes key={`${identity.session.generation}:${identity.session.status}`}>
            <Route path="/" element={<CatalogConnection><Overview /></CatalogConnection>} />
            <Route path="/products" element={<CatalogConnection><Catalog /></CatalogConnection>} />
            <Route path="/formulas" element={<CatalogConnection><Catalog formulas /></CatalogConnection>} />
            <Route path="/suppliers" element={<CatalogConnection><Materials suppliers /></CatalogConnection>} />
            <Route path="/materials" element={<CatalogConnection><Materials /></CatalogConnection>} />
            <Route path="/labels" element={<CatalogConnection><Labels /></CatalogConnection>} />
            <Route path="/impact" element={<CatalogConnection><Impact /></CatalogConnection>} />
            <Route path="/reviews" element={<Reviews />} />
            <Route path="/label-history" element={<LabelHistory />} />
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
            <span>{workspaceName} · Single-market MVP</span>
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
            <li>The preview profile is not a signed-in account. In an enabled course demo, the formula editor requires explicit Data Admin consent for saving and publishing. Label approvals use the separately verified connected identity.</li>
          </ol>
        </DialogContent>
      </Dialog>
    </div>
  )
}
