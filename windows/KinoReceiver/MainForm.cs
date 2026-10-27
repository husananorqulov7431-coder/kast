using System;
using System.Drawing;
using System.Windows.Forms;
using Microsoft.Web.WebView2.WinForms;

namespace KinoReceiver
{
    public class MainForm : Form
    {
        private WebView2 webView = null!;
        private Panel topBar = null!;
        private TextBox txtIp = null!;
        private Button btnConnect = null!;
        private Button btnFullscreen = null!;
        private System.Windows.Forms.Timer cursorTimer = null!;
        private Point lastMousePosition;
        private bool isFullscreen = false;
        private FormWindowState prevWindowState;
        private FormBorderStyle prevBorderStyle;

        public MainForm()
        {
            InitializeComponent();
            SetupCursorHiding();
        }

        private void InitializeComponent()
        {
            this.Text = "🎬 KinoReceiver - 16:9 Cinema Player";
            this.Size = new Size(1280, 750);
            this.MinimumSize = new Size(800, 480);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.BackColor = Color.Black;
            this.KeyPreview = true;

            // Top Bar
            topBar = new Panel
            {
                Dock = DockStyle.Top,
                Height = 44,
                BackColor = Color.FromArgb(20, 20, 20),
                Padding = new Padding(10, 6, 10, 6)
            };

            var lblTitle = new Label
            {
                Text = "🎬 KinoReceiver (Hotspot):",
                ForeColor = Color.White,
                AutoSize = true,
                Location = new Point(12, 12),
                Font = new Font("Segoe UI", 10, FontStyle.Bold)
            };

            txtIp = new TextBox
            {
                Text = "192.168.43.1",
                Width = 140,
                Location = new Point(210, 9),
                BackColor = Color.FromArgb(35, 35, 35),
                ForeColor = Color.LimeGreen,
                Font = new Font("Segoe UI", 10, FontStyle.Bold),
                BorderStyle = BorderStyle.FixedSingle
            };

            btnConnect = new Button
            {
                Text = "Ulanish",
                Location = new Point(360, 8),
                Width = 90,
                Height = 28,
                BackColor = Color.FromArgb(30, 136, 229),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btnConnect.FlatAppearance.BorderSize = 0;
            btnConnect.Click += (s, e) => ConnectToPhone();

            btnFullscreen = new Button
            {
                Text = "⛶ To'liq Ekran (F11)",
                Dock = DockStyle.Right,
                Width = 160,
                BackColor = Color.FromArgb(40, 40, 40),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9),
                Cursor = Cursors.Hand
            };
            btnFullscreen.FlatAppearance.BorderSize = 0;
            btnFullscreen.Click += (s, e) => ToggleFullscreen();

            topBar.Controls.Add(lblTitle);
            topBar.Controls.Add(txtIp);
            topBar.Controls.Add(btnConnect);
            topBar.Controls.Add(btnFullscreen);

            // WebView2
            webView = new WebView2
            {
                Dock = DockStyle.Fill
            };

            this.Controls.Add(webView);
            this.Controls.Add(topBar);

            this.KeyDown += MainForm_KeyDown;
        }

        protected override async void OnLoad(EventArgs e)
        {
            base.OnLoad(e);
            try
            {
                await webView.EnsureCoreWebView2Async();
                ConnectToPhone();
            }
            catch (Exception ex)
            {
                MessageBox.Show("WebView2 yuklanishida xatolik: " + ex.Message, "Xatolik", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void ConnectToPhone()
        {
            if (webView.CoreWebView2 == null) return;
            var ip = txtIp.Text.Trim();
            if (string.IsNullOrEmpty(ip)) ip = "192.168.43.1";
            var url = $"http://{ip}:8080/";
            webView.Source = new Uri(url);
        }

        private void ToggleFullscreen()
        {
            if (!isFullscreen)
            {
                prevWindowState = this.WindowState;
                prevBorderStyle = this.FormBorderStyle;

                topBar.Visible = false;
                this.FormBorderStyle = FormBorderStyle.None;
                this.WindowState = FormWindowState.Maximized;
                isFullscreen = true;
            }
            else
            {
                topBar.Visible = true;
                this.FormBorderStyle = prevBorderStyle;
                this.WindowState = prevWindowState;
                isFullscreen = false;
                Cursor.Show();
            }
        }

        private void MainForm_KeyDown(object? sender, KeyEventArgs e)
        {
            if (e.KeyCode == Keys.F11)
            {
                ToggleFullscreen();
                e.Handled = true;
            }
            else if (e.KeyCode == Keys.Escape && isFullscreen)
            {
                ToggleFullscreen();
                e.Handled = true;
            }
        }

        private void SetupCursorHiding()
        {
            cursorTimer = new System.Windows.Forms.Timer { Interval = 2500 };
            cursorTimer.Tick += (s, e) =>
            {
                if (isFullscreen && Cursor.Position == lastMousePosition)
                {
                    Cursor.Hide();
                }
            };
            cursorTimer.Start();

            this.MouseMove += (s, e) =>
            {
                Cursor.Show();
                lastMousePosition = Cursor.Position;
            };
        }
    }
}
