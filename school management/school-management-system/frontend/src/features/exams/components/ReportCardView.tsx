import PrintIcon from '@mui/icons-material/Print';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import GlobalStyles from '@mui/material/GlobalStyles';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import type { ReportCard, SubjectResult } from '../../../types/exams';
import { formatDate } from '../../../utils/format';
import { formatDateRange, formatInstant, formatNumber, formatPercent } from '../labels';
import { ResultChip, SubjectStatusChip } from './StatusChips';

/** While a report card is on screen, printing shows only the card, in black on white. */
const printStyles = {
  '@page': { margin: '12mm' },
  '@media print': {
    body: { background: '#fff !important' },
    '.MuiAppBar-root, .MuiDrawer-root, .MuiSnackbar-root, .no-print, a[href="#main-content"]': {
      display: 'none !important',
    },
    '#main-content': { padding: '0 !important' },
    '#main-content > .MuiToolbar-root': { display: 'none !important' },
    '.report-card, .report-card *': {
      color: '#000 !important',
      background: 'transparent !important',
    },
    '.report-card .MuiChip-root': { border: '1px solid #000 !important' },
  },
};

const onScreenOnly = { display: { xs: 'block', sm: 'none' }, '@media print': { display: 'none' } };
const tableOrPrint = { display: { xs: 'none', sm: 'block' }, '@media print': { display: 'block' } };

const marksText = (subject: SubjectResult) =>
  subject.absent
    ? 'Absent'
    : `${formatNumber(subject.marks ?? 0)} / ${formatNumber(subject.maxMarks)}`;

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography component="dt" variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0, fontWeight: 600, overflowWrap: 'anywhere' }}>
        {children}
      </Typography>
    </Box>
  );
}

interface ReportCardViewProps {
  card: ReportCard;
  /** True for a locked exam whose results are not published yet. */
  preview?: boolean;
  /** Shown beside the print button, for example a link back to the list. */
  actions?: ReactNode;
}

/** The printable report card of one student for one exam. */
export function ReportCardView({ card, preview = false, actions }: ReportCardViewProps) {
  const { result } = card;

  return (
    <Box>
      <GlobalStyles styles={printStyles} />
      <Stack
        className="no-print"
        direction="row"
        spacing={1}
        useFlexGap
        sx={{ mb: 2, flexWrap: 'wrap', alignItems: 'center' }}
      >
        {actions}
        <Button variant="contained" startIcon={<PrintIcon />} onClick={() => window.print()}>
          Print
        </Button>
      </Stack>
      {preview ? (
        <Alert severity="warning" className="no-print" sx={{ mb: 2 }}>
          Preview - not published. Students cannot see this report card yet.
        </Alert>
      ) : null}

      <Paper
        className="report-card"
        component="article"
        aria-label={`Report card of ${result.studentName}`}
        variant="outlined"
        sx={{
          maxWidth: 860,
          mx: 'auto',
          p: { xs: 2, sm: 4 },
          '@media print': { maxWidth: 'none', border: 0, boxShadow: 'none', p: 0 },
        }}
      >
        <Box sx={{ textAlign: 'center', mb: 3 }}>
          <Typography
            variant="h5"
            component="h1"
            sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}
          >
            {card.schoolName}
          </Typography>
          <Typography variant="h6" component="p" sx={{ overflowWrap: 'anywhere' }}>
            Report card · {card.examName}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {card.academicYearName} · {card.gradeLevelName}
            {card.startDate || card.endDate
              ? ` · ${formatDateRange(card.startDate, card.endDate, formatDate)}`
              : ''}
          </Typography>
        </Box>

        <Box
          component="dl"
          sx={{
            m: 0,
            mb: 3,
            display: 'grid',
            gap: 2,
            gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' },
          }}
        >
          <Fact label="Student">{result.studentName}</Fact>
          <Fact label="Admission no.">{result.admissionNumber}</Fact>
          <Fact label="Class">{result.sectionName}</Fact>
          <Fact label="Roll no.">{result.rollNumber ?? '-'}</Fact>
        </Box>

        <Box sx={tableOrPrint}>
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table size="small" aria-label="Subject results">
              <TableHead>
                <TableRow>
                  <TableCell>Subject</TableCell>
                  <TableCell align="right">Marks</TableCell>
                  <TableCell align="right">%</TableCell>
                  <TableCell>Grade</TableCell>
                  <TableCell>Result</TableCell>
                  <TableCell>Remarks</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {result.subjects.map((subject) => (
                  <TableRow key={subject.subjectId}>
                    <TableCell sx={{ overflowWrap: 'anywhere' }}>{subject.subjectName}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      {marksText(subject)}
                    </TableCell>
                    <TableCell align="right">{formatPercent(subject.percentage)}</TableCell>
                    <TableCell>{subject.grade ?? '-'}</TableCell>
                    <TableCell>
                      <SubjectStatusChip status={subject.status} />
                    </TableCell>
                    <TableCell sx={{ overflowWrap: 'anywhere' }}>{subject.remarks ?? ''}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
        <Stack
          component="ul"
          aria-label="Subject results by subject"
          spacing={1.5}
          sx={{ listStyle: 'none', m: 0, p: 0, ...onScreenOnly }}
        >
          {result.subjects.map((subject) => (
            <Box
              key={subject.subjectId}
              component="li"
              sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 1.5 }}
            >
              <Stack
                direction="row"
                spacing={1}
                sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}
              >
                <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
                  {subject.subjectName}
                </Typography>
                <SubjectStatusChip status={subject.status} />
              </Stack>
              <Typography variant="body2">
                Marks: {marksText(subject)} · {formatPercent(subject.percentage)} · Grade{' '}
                {subject.grade ?? '-'}
              </Typography>
              {subject.remarks ? (
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ overflowWrap: 'anywhere' }}
                >
                  {subject.remarks}
                </Typography>
              ) : null}
            </Box>
          ))}
        </Stack>

        <Box
          component="dl"
          sx={{
            m: 0,
            mt: 3,
            p: 2,
            border: 1,
            borderColor: 'divider',
            borderRadius: 1,
            display: 'grid',
            gap: 2,
            gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' },
          }}
        >
          <Fact label="Total marks">
            {formatNumber(result.totalMarks)} / {formatNumber(result.totalMax)}
          </Fact>
          <Fact label="Overall percentage">{formatPercent(result.percentage)}</Fact>
          <Fact label="Overall grade">{result.grade ?? '-'}</Fact>
          <Fact label="Result">
            <ResultChip result={result.result} />
          </Fact>
        </Box>

        <Box sx={{ mt: 3 }}>
          <Typography variant="subtitle2" component="h2">
            Class teacher&apos;s comment
          </Typography>
          <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
            {result.comment ?? 'No comment'}
          </Typography>
        </Box>
        {card.publishedAt ? (
          <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 3 }}>
            Published on {formatInstant(card.publishedAt)}
          </Typography>
        ) : null}
      </Paper>
    </Box>
  );
}
