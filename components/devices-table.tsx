"use client"

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import { Pencil, Trash2 } from "lucide-react"
import type { Device } from "@/hooks/use-devices"

interface DevicesTableProps {
  devices: Device[]
  onEdit: (device: Device) => void
  onDelete: (device: Device) => void
  canEdit?: boolean
  canDelete?: boolean
}

const statusColors: Record<string, string> = {
  AVAILABLE: "bg-green-100 text-green-800",
  ASSIGNED: "bg-blue-100 text-blue-800",
  LOST: "bg-red-100 text-red-800",
  DAMAGED: "bg-orange-100 text-orange-800",
}

export function DevicesTable({
  devices,
  onEdit,
  onDelete,
  canEdit = true,
  canDelete = true,
}: DevicesTableProps) {
  const showActions = canEdit || canDelete
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-[60px]">ID</TableHead>
          <TableHead>IMEI</TableHead>
          <TableHead>Model</TableHead>
          <TableHead>SIM Number</TableHead>
          <TableHead>Customer</TableHead>
          <TableHead>Status</TableHead>
          {showActions && <TableHead className="w-[100px] text-right">Actions</TableHead>}
        </TableRow>
      </TableHeader>
      <TableBody>
        {devices.map((device) => (
          <TableRow key={device.id}>
            <TableCell className="font-medium">{device.id}</TableCell>
            <TableCell className="font-mono text-sm">{device.imei}</TableCell>
            <TableCell>{device.model}</TableCell>
            <TableCell className="font-mono text-sm">{device.sim_number}</TableCell>
            <TableCell>{device.customer_name || "-"}</TableCell>
            <TableCell>
              <span
                className={`rounded-full px-2 py-1 text-xs font-medium ${statusColors[device.status]}`}
              >
                {device.status}
              </span>
            </TableCell>
            {showActions && (
              <TableCell className="text-right">
                <div className="flex justify-end gap-1">
                  {canEdit && (
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Edit ${device.model} ${device.imei}`}
                      onClick={() => onEdit(device)}
                    >
                      <Pencil className="h-4 w-4" />
                    </Button>
                  )}
                  {canDelete && (
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Delete ${device.model} ${device.imei}`}
                      onClick={() => onDelete(device)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>
              </TableCell>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
