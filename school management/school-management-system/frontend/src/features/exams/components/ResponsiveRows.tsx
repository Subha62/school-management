import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import type { ReactNode } from 'react';

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

export interface RowColumn<T> {
  id: string;
  header: string;
  render: (row: T) => ReactNode;
  align?: 'right';
}

interface ResponsiveRowsProps<T> {
  /** Accessible name of the table or list. */
  label: string;
  rows: T[];
  /** The first column is the heading of each card on narrow screens. */
  columns: RowColumn<T>[];
  getRowId: (row: T) => string;
  /** Buttons for one row, shown in a last column or under the card. */
  actions?: (row: T) => ReactNode;
}

/** Rows that are a table from the `md` breakpoint and a stack of cards below it, so nothing scrolls sideways. */
export function ResponsiveRows<T>({
  label,
  rows,
  columns,
  getRowId,
  actions,
}: ResponsiveRowsProps<T>) {
  const theme = useTheme();
  const wide = useMediaQuery(theme.breakpoints.up('md'));
  const [primary, ...rest] = columns;

  if (wide) {
    return (
      <Paper variant="outlined">
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table aria-label={label}>
            <TableHead>
              <TableRow>
                {columns.map((column) => (
                  <TableCell key={column.id} align={column.align}>
                    {column.header}
                  </TableCell>
                ))}
                {actions ? (
                  <TableCell align="right">
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
                      align={column.align}
                      sx={{ overflowWrap: 'anywhere' }}
                    >
                      {column.render(row)}
                    </TableCell>
                  ))}
                  {actions ? <TableCell align="right">{actions(row)}</TableCell> : null}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    );
  }

  return (
    <Stack component="ul" aria-label={label} spacing={1.5} sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {rows.map((row) => (
        <Card key={getRowId(row)} component="li" variant="outlined">
          <CardContent sx={{ '&:last-child': { pb: 2 } }}>
            <Typography
              variant="subtitle1"
              component="div"
              sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}
            >
              {primary?.render(row)}
            </Typography>
            <Stack component="dl" spacing={0.5} sx={{ m: 0, mt: 1 }}>
              {rest.map((column) => (
                <Stack key={column.id} direction="row" spacing={1} sx={{ alignItems: 'baseline' }}>
                  <Typography
                    component="dt"
                    variant="body2"
                    color="text.secondary"
                    sx={{ width: 104, flexShrink: 0 }}
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
            {actions ? (
              <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 1, flexWrap: 'wrap' }}>
                {actions(row)}
              </Stack>
            ) : null}
          </CardContent>
        </Card>
      ))}
    </Stack>
  );
}
