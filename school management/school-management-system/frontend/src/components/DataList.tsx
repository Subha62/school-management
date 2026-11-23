import MoreVertIcon from '@mui/icons-material/MoreVert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useState, type ReactNode } from 'react';
import type { ApiError } from '../api/errors';
import type { PageResponse } from '../types/api';
import { EmptyState } from './EmptyState';
import { ErrorState } from './ErrorState';
import { LoadingState } from './LoadingState';

export interface Column<T> {
  id: string;
  header: string;
  render: (row: T) => ReactNode;
  /** Table only: hide this column below the given breakpoint. Cards always show every column. */
  hideBelow?: 'sm' | 'md' | 'lg';
}

export interface RowAction<T> {
  label: string;
  onSelect: (row: T) => void;
  destructive?: boolean;
  hidden?: (row: T) => boolean;
}

interface DataListProps<T> {
  /** Server page; the list is empty while it is `undefined`. */
  data: PageResponse<T> | undefined;
  columns: Column<T>[];
  getRowId: (row: T) => string;
  /** Accessible name of the row, used by its action menu ("Actions for Grade 8"). */
  getRowLabel: (row: T) => string;
  actions?: RowAction<T>[];
  isLoading: boolean;
  error: ApiError | null;
  onRetry: () => void;
  emptyTitle: string;
  emptyHint?: string;
  emptyAction?: ReactNode;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}

const PAGE_SIZES = [10, 20, 50];

function RowMenu<T>({ row, actions, label }: { row: T; actions: RowAction<T>[]; label: string }) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const visible = actions.filter((action) => !action.hidden?.(row));
  if (visible.length === 0) {
    return null;
  }
  return (
    <>
      <IconButton
        aria-label={`Actions for ${label}`}
        aria-haspopup="menu"
        onClick={(event) => setAnchor(event.currentTarget)}
      >
        <MoreVertIcon />
      </IconButton>
      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
        {visible.map((action) => (
          <MenuItem
            key={action.label}
            sx={action.destructive ? { color: 'error.main' } : undefined}
            onClick={() => {
              setAnchor(null);
              action.onSelect(row);
            }}
          >
            {action.label}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}

/**
 * A paged list that is a table from the `md` breakpoint up and a stack of cards below it, so important data
 * and actions stay readable on phones without sideways scrolling.
 */
export function DataList<T>({
  data,
  columns,
  getRowId,
  getRowLabel,
  actions = [],
  isLoading,
  error,
  onRetry,
  emptyTitle,
  emptyHint,
  emptyAction,
  onPageChange,
  onPageSizeChange,
}: DataListProps<T>) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const rows = data?.content ?? [];

  if (error && !data) {
    return <ErrorState error={error} onRetry={onRetry} />;
  }
  if (isLoading && !data) {
    return <LoadingState label="Loading list" />;
  }
  if (rows.length === 0) {
    return (
      <Paper variant="outlined">
        <EmptyState title={emptyTitle} hint={emptyHint} action={emptyAction} />
      </Paper>
    );
  }

  const [primary, ...rest] = columns;

  return (
    <Paper variant="outlined">
      {error ? (
        <Box sx={{ p: 2 }}>
          <ErrorState title="Unable to refresh" error={error} onRetry={onRetry} />
        </Box>
      ) : null}
      {isMobile ? (
        <Stack component="ul" spacing={1.5} sx={{ listStyle: 'none', m: 0, p: 1.5 }}>
          {rows.map((row) => (
            <Card component="li" key={getRowId(row)} variant="outlined">
              <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                <Stack
                  direction="row"
                  sx={{ alignItems: 'flex-start', justifyContent: 'space-between' }}
                >
                  <Typography
                    variant="subtitle1"
                    component="div"
                    sx={{ fontWeight: 600, minWidth: 0, overflowWrap: 'anywhere' }}
                  >
                    {primary?.render(row)}
                  </Typography>
                  <RowMenu row={row} actions={actions} label={getRowLabel(row)} />
                </Stack>
                <Stack component="dl" spacing={0.5} sx={{ m: 0, mt: 1 }}>
                  {rest.map((column) => (
                    <Stack
                      key={column.id}
                      direction="row"
                      spacing={1}
                      sx={{ alignItems: 'baseline' }}
                    >
                      <Typography
                        component="dt"
                        variant="body2"
                        color="text.secondary"
                        sx={{ width: 112, flexShrink: 0 }}
                      >
                        {column.header}
                      </Typography>
                      <Typography
                        component="dd"
                        variant="body2"
                        sx={{ m: 0, minWidth: 0, overflowWrap: 'anywhere' }}
                      >
                        {column.render(row)}
                      </Typography>
                    </Stack>
                  ))}
                </Stack>
              </CardContent>
            </Card>
          ))}
        </Stack>
      ) : (
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table size="medium">
            <TableHead>
              <TableRow>
                {columns.map((column) => (
                  <TableCell
                    key={column.id}
                    sx={
                      column.hideBelow
                        ? { display: { xs: 'none', [column.hideBelow]: 'table-cell' } }
                        : undefined
                    }
                  >
                    {column.header}
                  </TableCell>
                ))}
                {actions.length > 0 ? (
                  <TableCell align="right" sx={{ width: 64 }}>
                    <Box component="span" sx={visuallyHidden}>
                      Actions
                    </Box>
                  </TableCell>
                ) : null}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={getRowId(row)} hover>
                  {columns.map((column) => (
                    <TableCell
                      key={column.id}
                      sx={
                        column.hideBelow
                          ? { display: { xs: 'none', [column.hideBelow]: 'table-cell' } }
                          : undefined
                      }
                    >
                      {column.render(row)}
                    </TableCell>
                  ))}
                  {actions.length > 0 ? (
                    <TableCell align="right">
                      <RowMenu row={row} actions={actions} label={getRowLabel(row)} />
                    </TableCell>
                  ) : null}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      <TablePagination
        component="div"
        count={data?.totalElements ?? 0}
        page={data?.page ?? 0}
        rowsPerPage={data?.size ?? PAGE_SIZES[1]!}
        rowsPerPageOptions={PAGE_SIZES}
        onPageChange={(_event, page) => onPageChange(page)}
        onRowsPerPageChange={(event) => onPageSizeChange(Number(event.target.value))}
        labelRowsPerPage="Rows"
        sx={{
          '.MuiTablePagination-toolbar': { flexWrap: 'wrap', justifyContent: 'center', px: 1 },
          '.MuiTablePagination-spacer': { display: { xs: 'none', sm: 'block' } },
        }}
      />
    </Paper>
  );
}

const visuallyHidden = {
  border: 0,
  clip: 'rect(0 0 0 0)',
  height: '1px',
  margin: '-1px',
  overflow: 'hidden',
  padding: 0,
  position: 'absolute',
  whiteSpace: 'nowrap',
  width: '1px',
} as const;
