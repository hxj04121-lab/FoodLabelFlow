import { Layout, Menu, Typography } from 'antd'
import { DatabaseOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { HealthPage } from './pages/HealthPage'

const { Content, Header, Sider } = Layout

export default function App() {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth="0">
        <div style={{ color: '#fff', fontSize: 18, fontWeight: 600, padding: 20 }}>SpecTrace</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={['health']}
          items={[
            { key: 'health', icon: <SafetyCertificateOutlined />, label: 'Platform health' },
            { key: 'database', icon: <DatabaseOutlined />, label: 'Database baseline' },
          ]}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', paddingInline: 24 }}>
          <Typography.Text strong>Food label change traceability workspace</Typography.Text>
        </Header>
        <Content style={{ margin: 24 }}>
          <HealthPage />
        </Content>
      </Layout>
    </Layout>
  )
}
