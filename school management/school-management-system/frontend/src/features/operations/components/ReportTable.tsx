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
import { EmptyState } from '../../../components/EmptyState';
import { StatusChip } from '../../../components/StatusChip';
import type { ReportRow } from '../../../types/operations';
import { formatPercentage } from '../labels';

interface ReportTableProps {
  rows: ReportRow[];
  caption: string;
  emptyTitle: string;
  emptyHint?: string;
  /** Show the class of each student (useful when the report spans several classes). */
  showClass?: boolean;
}

function Percentage({ row }: { row: ReportRow }) {
  return row.percentage === null ? (
    <Typography component="span" color="text.secondary">
      No data
    </Typography>
  ) : (
    <StatusChip
      label={formatPercentage(row.percentage)}
      tone={row.belowThreshold ? 'warning' : 'success'}
    />
  );
}

/** A report as a table from the `md` breakpoint and as cards below it. */
export function ReportTable({ rows, caption, emptyTitle, emptyHint, showClass }: ReportTableProps) {
  const theme = useTheme();
  const wide = useMediaQuery(theme.breakpoints.up('md'));

  if (rows.length === 0) {
    return <EmptyState title={emptyTitle} hint={emptyHint} />;
  }

  if (!wide) {
    return (
      <Stack
        component="ul"
        spacing={1.5}
        aria-label={caption}
        sx={{ m: 0, p: 0, listStyle: 'none' }}
      >
        {rows.map((row) => (
          <Card key={row.studentId} component="li" variant="outlined">
            <CardContent sx={{ '&:last-child': { pb: 2 } }}>
              <Box
                sx={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  gap: 1,
                  alignItems: 'start',
                }}
              >
                <Box sx={{ minWidth: 0 }}>
                  <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
                    {row.name}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {row.admissionNumber}
                    {showClass ? ` · ${row.sectionName}` : ''}
                  </Typography>
                </Box>
                <Percentage row={row} />
              </Box>
              <Typography variant="body2" sx={{ mt: 1 }}>
                Present {row.present} · Late {row.late} · Half day {row.halfDay} · Absent{' '}
                {row.absent} · Excused {row.excused}
              </Typography>
            </CardContent>
          </Card>
        ))}
      </Stack>
    );
  }

  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small" aria-label={caption}>
        <TableHead>
          <TableRow>
            <TableCell>Admission no.</TableCell>
            <TableCell>Student</TableCell>
            {showClass ? <TableCell>Class</TableCell> : null}
            <TableCell align="right">Present</TableCell>
            <TableCell align="right">Late</TableCell>
            <TableCell align="right">Half day</TableCell>
            <TableCell align="right">Absent</TableCell>
            <TableCell align="right">Excused</TableCell>
            <TableCell>Attendance</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.studentId}>
              <TableCell>{row.admissionNumber}</TableCell>
              <TableCell>{row.name}</TableCell>
              {showClass ? <TableCell>{row.sectionName}</TableCell> : null}
              <TableCell align="right">{row.present}</TableCell>
              <TableCell align="right">{row.late}</TableCell>
              <TableCell align="right">{row.halfDay}</TableCell>
              <TableCell align="right">{row.absent}</TableCell>
              <TableCell align="right">{row.excused}</TableCell>
              <TableCell>
                <Percentage row={row} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
