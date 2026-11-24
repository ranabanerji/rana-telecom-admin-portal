"use client"

import { AppSidebar } from "@/components/app-sidebar"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbList,
  BreadcrumbPage,
} from "@/components/ui/breadcrumb"
import { Separator } from "@/components/ui/separator"
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { HealthStatus } from "@/components/health-status"
import { useDashboardStats } from "@/hooks/use-dashboard-stats"
import { planColors } from "@/lib/chart-colors"
import { Users, DollarSign, Ticket, Smartphone } from "lucide-react"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import { Bar, BarChart, Pie, PieChart, XAxis, YAxis, CartesianGrid } from "recharts"

function planChartConfig(
  dataKey: string,
  label: string,
  plans: { plan_id: number; name: string }[],
  colors: Map<number, string>
): ChartConfig {
  return Object.fromEntries([
    [dataKey, { label }],
    ...plans.map((plan) => [plan.name, { label: plan.name, color: colors.get(plan.plan_id) }]),
  ])
}

const devicesByStatusConfig: ChartConfig = {
  value: { label: "Devices" },
  ASSIGNED: { label: "Assigned", color: "var(--chart-1)" },
  AVAILABLE: { label: "Available", color: "var(--chart-3)" },
  DAMAGED: { label: "Damaged", color: "var(--chart-5)" },
  LOST: { label: "Lost", color: "#e82010" },
}

const ticketsByStatusConfig: ChartConfig = {
  value: { label: "Tickets" },
  OPEN: { label: "Open", color: "var(--chart-5)" },
  IN_PROGRESS: { label: "In Progress", color: "var(--chart-1)" },
  RESOLVED: { label: "Resolved", color: "var(--chart-3)" },
  CLOSED: { label: "Closed", color: "var(--chart-4)" },
}

export default function DashboardPage() {
  const {
    stats,
    customersByPlan,
    devicesByStatus,
    ticketsByStatus,
    revenueByPlan,
    loading,
    error,
    chartErrors,
  } = useDashboardStats()

  const colorByPlan = planColors([...customersByPlan, ...revenueByPlan].map((plan) => plan.plan_id))
  const customersByPlanConfig = planChartConfig("value", "Customers", customersByPlan, colorByPlan)
  const revenueByPlanConfig = planChartConfig("revenue", "Revenue", revenueByPlan, colorByPlan)

  if (loading) {
    return (
      <SidebarProvider>
        <AppSidebar />
        <SidebarInset>
          <div className="flex h-screen items-center justify-center">
            <div className="text-muted-foreground">Loading dashboard...</div>
          </div>
        </SidebarInset>
      </SidebarProvider>
    )
  }

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <header className="flex h-16 shrink-0 items-center gap-2 transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-12">
          <div className="flex flex-1 items-center gap-2 px-4">
            <SidebarTrigger className="-ml-1" />
            <Separator orientation="vertical" className="mr-2 data-[orientation=vertical]:h-4" />
            <Breadcrumb>
              <BreadcrumbList>
                <BreadcrumbItem>
                  <BreadcrumbPage>Dashboard</BreadcrumbPage>
                </BreadcrumbItem>
              </BreadcrumbList>
            </Breadcrumb>
            <div className="ml-auto">
              <HealthStatus />
            </div>
          </div>
        </header>

        <div className="flex flex-1 flex-col gap-4 p-4 pt-0">
          {error && (
            <p role="alert" className="text-red-500">
              {error}
            </p>
          )}
          {/* KPI Cards */}
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-body-small">Active Customers</CardTitle>
                <Users className="text-muted-foreground h-4 w-4" />
              </CardHeader>
              <CardContent>
                <div className="text-headline-2">{stats?.active_customers || 0}</div>
                <p className="text-caption text-muted-foreground">Total active subscribers</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-body-small">Monthly Revenue</CardTitle>
                <DollarSign className="text-muted-foreground h-4 w-4" />
              </CardHeader>
              <CardContent>
                <div className="text-headline-2">
                  ${stats?.monthly_revenue?.toFixed(2) || "0.00"}
                </div>
                <p className="text-caption text-muted-foreground">From active subscriptions</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-body-small">Open Tickets</CardTitle>
                <Ticket className="text-muted-foreground h-4 w-4" />
              </CardHeader>
              <CardContent>
                <div className="text-headline-2">{stats?.open_tickets || 0}</div>
                <p className="text-caption text-muted-foreground">Requiring attention</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-body-small">Devices in Use</CardTitle>
                <Smartphone className="text-muted-foreground h-4 w-4" />
              </CardHeader>
              <CardContent>
                <div className="text-headline-2">{stats?.devices_in_use || 0}</div>
                <p className="text-caption text-muted-foreground">Assigned to customers</p>
              </CardContent>
            </Card>
          </div>

          {/* Charts Row 1 */}
          <div className="grid gap-4 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="text-headline-3">Customers by Plan</CardTitle>
              </CardHeader>
              <CardContent>
                {chartErrors["customers-by-plan"] ? (
                  <p
                    role="alert"
                    className="flex h-[250px] items-center justify-center text-red-500"
                  >
                    {chartErrors["customers-by-plan"]}
                  </p>
                ) : (
                  <ChartContainer
                    config={customersByPlanConfig}
                    className="mx-auto h-[250px] w-full"
                  >
                    <PieChart accessibilityLayer>
                      <ChartTooltip
                        cursor={false}
                        content={<ChartTooltipContent hideLabel nameKey="name" />}
                      />
                      <Pie
                        data={customersByPlan.map((item) => ({
                          ...item,
                          fill: colorByPlan.get(item.plan_id),
                        }))}
                        dataKey="value"
                        nameKey="name"
                        innerRadius={60}
                        outerRadius={100}
                      />
                    </PieChart>
                  </ChartContainer>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-headline-3">Revenue by Plan</CardTitle>
              </CardHeader>
              <CardContent>
                {chartErrors["revenue-by-plan"] ? (
                  <p
                    role="alert"
                    className="flex h-[250px] items-center justify-center text-red-500"
                  >
                    {chartErrors["revenue-by-plan"]}
                  </p>
                ) : (
                  <ChartContainer config={revenueByPlanConfig} className="h-[250px] w-full">
                    <BarChart
                      accessibilityLayer
                      data={revenueByPlan.map((item) => ({
                        ...item,
                        fill: colorByPlan.get(item.plan_id),
                      }))}
                    >
                      <CartesianGrid vertical={false} />
                      <XAxis dataKey="name" tickLine={false} axisLine={false} tickMargin={10} />
                      <YAxis
                        tickLine={false}
                        axisLine={false}
                        tickFormatter={(value) => `$${value}`}
                      />
                      <ChartTooltip content={<ChartTooltipContent nameKey="name" />} />
                      <Bar dataKey="revenue" radius={4} />
                    </BarChart>
                  </ChartContainer>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Charts Row 2 */}
          <div className="grid gap-4 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="text-headline-3">Device Status</CardTitle>
              </CardHeader>
              <CardContent>
                {chartErrors["devices-by-status"] ? (
                  <p
                    role="alert"
                    className="flex h-[250px] items-center justify-center text-red-500"
                  >
                    {chartErrors["devices-by-status"]}
                  </p>
                ) : (
                  <ChartContainer config={devicesByStatusConfig} className="h-[250px] w-full">
                    <BarChart
                      accessibilityLayer
                      data={devicesByStatus.map((item) => ({
                        ...item,
                        fill:
                          devicesByStatusConfig[item.name as keyof typeof devicesByStatusConfig]
                            ?.color || "var(--chart-4)",
                      }))}
                      layout="vertical"
                    >
                      <CartesianGrid horizontal={false} />
                      <XAxis type="number" tickLine={false} axisLine={false} />
                      <YAxis
                        dataKey="name"
                        type="category"
                        tickLine={false}
                        axisLine={false}
                        tickMargin={10}
                        width={85}
                      />
                      <ChartTooltip content={<ChartTooltipContent nameKey="name" />} />
                      <Bar dataKey="value" radius={4} />
                    </BarChart>
                  </ChartContainer>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-headline-3">Ticket Status</CardTitle>
              </CardHeader>
              <CardContent>
                {chartErrors["tickets-by-status"] ? (
                  <p
                    role="alert"
                    className="flex h-[250px] items-center justify-center text-red-500"
                  >
                    {chartErrors["tickets-by-status"]}
                  </p>
                ) : (
                  <ChartContainer
                    config={ticketsByStatusConfig}
                    className="mx-auto h-[250px] w-full"
                  >
                    <PieChart accessibilityLayer>
                      <ChartTooltip
                        cursor={false}
                        content={<ChartTooltipContent hideLabel nameKey="name" />}
                      />
                      <Pie
                        data={ticketsByStatus.map((item) => ({
                          ...item,
                          fill:
                            ticketsByStatusConfig[item.name as keyof typeof ticketsByStatusConfig]
                              ?.color || "var(--chart-4)",
                        }))}
                        dataKey="value"
                        nameKey="name"
                        innerRadius={60}
                        outerRadius={100}
                      />
                    </PieChart>
                  </ChartContainer>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
