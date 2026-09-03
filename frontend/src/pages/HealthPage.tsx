import { Alert, Card, Descriptions, Spin, Tag, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { getHealth, type HealthResponse } from '../api/client'

const { Paragraph, Text, Title } = Typography

export function HealthPage() {
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getHealth().then(setHealth).catch((requestError: unknown) => {
      setError(requestError instanceof Error ? requestError.message : 'Health request failed')
    })
  }, [])

  return (
    <Card>
      <Title level={2}>SpecTrace platform baseline</Title>
      <Paragraph>
        Stage 0 provides the shared application shell and a truthful runtime health check.
        Domain slices are handed to Sprint 1 work orders.
      </Paragraph>
      {error ? <Alert type="error" message={error} /> : null}
      {!health && !error ? <Spin aria-label="Loading health" /> : null}
      {health ? (
        <Descriptions bordered column={1}>
          <Descriptions.Item label="Application">
            <Tag color={health.status === 'ok' ? 'green' : 'orange'}>{health.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Database">
            <Text>{health.database}</Text>
          </Descriptions.Item>
        </Descriptions>
      ) : null}
    </Card>
  )
}
