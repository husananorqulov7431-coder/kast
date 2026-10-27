using System;
using System.Drawing;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Windows.Forms;
using Microsoft.Web.WebView2.WinForms;

namespace KinoReceiver
{
    public class MainForm : Form
    {
        private WebView2 webView = null!;
        private Panel topBar = null!;
        private TextBox txtPin = null!;
        private TextBox txtIp = null!;
        private Button btnConnect = null!;
        private Button btnFullscreen = null!;
        private Label lblStatus = null!;
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
            this.Text = "🎬 KinoReceiver v2.0 - 16:9 Cinema Player";
            this.Size = new Size(1280, 750);
            this.MinimumSize = new Size(850, 500);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.BackColor = Color.Black;
            this.KeyPreview = true;

            // Top Bar
            topBar = new Panel
            {
                Dock = DockStyle.Top,
                Height = 48,
                BackColor = Color.FromArgb(24, 24, 24),
                Padding = new Padding(12, 8, 12, 8)
            };

            var lblTitle = new Label
            {
                Text = "🎬 PIN:",
                ForeColor = Color.FromArgb(0, 230, 118),
                AutoSize = true,
                Location = new Point(12, 14),
                Font = new Font("Segoe UI", 11, FontStyle.Bold)
            };

            txtPin = new TextBox
            {
                Width = 90,
                Location = new Point(65, 10),
                MaxLength = 6,
                BackColor = Color.FromArgb(38, 38, 38),
                ForeColor = Color.FromArgb(0, 230, 118),
                Font = new Font("Segoe UI", 12, FontStyle.Bold),
                TextAlign = HorizontalAlignment.Center,
                BorderStyle = BorderStyle.FixedSingle
            };
            txtPin.KeyPress += (s, e) =>
            {
                if (e.KeyChar == (char)Keys.Enter)
                {
                    ConnectToPhone();
                    e.Handled = true;
                }
            };

            var lblIp = new Label
            {
                Text = "IP:",
                ForeColor = Color.Gray,
                AutoSize = true,
                Location = new Point(168, 15),
                Font = new Font("Segoe UI", 9)
            };

            var detectedIp = DetectGatewayIp();
            txtIp = new TextBox
            {
                Text = detectedIp,
                Width = 125,
                Location = new Point(190, 12),
                BackColor = Color.FromArgb(32, 32, 32),
                ForeColor = Color.White,
                Font = new Font("Segoe UI", 9),
                BorderStyle = BorderStyle.FixedSingle
            };

            btnConnect = new Button
            {
                Text = "Ulanish",
                Location = new Point(325, 9),
                Width = 95,
                Height = 30,
                BackColor = Color.FromArgb(30, 136, 229),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btnConnect.FlatAppearance.BorderSize = 0;
            btnConnect.Click += (s, e) => ConnectToPhone();

            lblStatus = new Label
            {
                Text = "PIN kiriting va Ulanishni bosing",
                ForeColor = Color.FromArgb(255, 179, 0),
                AutoSize = true,
                Location = new Point(430, 15),
                Font = new Font("Segoe UI", 9, FontStyle.Italic)
            };

            btnFullscreen = new Button
            {
                Text = "⛶ To'liq Ekran (F11)",
                Dock = DockStyle.Right,
                Width = 150,
                BackColor = Color.FromArgb(40, 40, 40),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9),
                Cursor = Cursors.Hand
            };
            btnFullscreen.FlatAppearance.BorderSize = 0;
            btnFullscreen.Click += (s, e) => ToggleFullscreen();

            topBar.Controls.Add(lblTitle);
            topBar.Controls.Add(txtPin);
            topBar.Controls.Add(lblIp);
            topBar.Controls.Add(txtIp);
            topBar.Controls.Add(btnConnect);
            topBar.Controls.Add(lblStatus);
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
                txtPin.Focus();
            }
            catch (Exception ex)
            {
                MessageBox.Show("WebView2 yuklanishida xatolik: " + ex.Message, "Xatolik", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        private void ConnectToPhone()
        {
            if (webView.CoreWebView2 == null) return;
            var ip = txtIp.Text.Trim();
            if (string.IsNullOrEmpty(ip)) ip = "192.168.43.1";

            var pin = txtPin.Text.Trim();
            if (pin.Length < 4)
            {
                lblStatus.Text = "Kamida 4 ta raqamdan iborat PIN kiriting!";
                lblStatus.ForeColor = Color.FromArgb(255, 82, 82);
                return;
            }

            lblStatus.Text = "Ulanmoqda...";
            lblStatus.ForeColor = Color.FromArgb(0, 230, 118);

            var url = $"http://{ip}:8080/?pin={Uri.EscapeDataString(pin)}";
            webView.Source = new Uri(url);
        }

        private string DetectGatewayIp()
        {
            try
            {
                foreach (var netInterface in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (netInterface.OperationalStatus == OperationalStatus.Up &&
                        (netInterface.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 ||
                         netInterface.NetworkInterfaceType == NetworkInterfaceType.Ethernet))
                    {
                        var props = netInterface.GetIPProperties();
                        foreach (var gateway in props.GatewayAddresses)
                        {
                            if (gateway.Address.AddressFamily == AddressFamily.InterNetwork)
                            {
                                var ip = gateway.Address.ToString();
                                if (!string.IsNullOrEmpty(ip) && ip != "0.0.0.0") return ip;
                            }
                        }
                    }
                }
            }
            catch { }
            return "192.168.43.1";
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
